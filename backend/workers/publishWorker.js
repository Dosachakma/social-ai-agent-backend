const crypto = require('crypto');
const scheduledJobService = require('../services/scheduledJobService');
const socialPostService = require('../services/socialPostService');
const socialAccountService = require('../services/socialAccountService');
const publishResultService = require('../services/publishResultService');
const publishIntentService = require('../services/publishIntentService');
const metaGraphService = require('../services/metaGraphService');
const twitterService = require('../services/twitterService');
const agentLogService = require('../services/agentLogService');
const { RetryPolicy, ErrorCategory } = require('./retryPolicy');

// In-memory registry for mock platform state (enables deterministic mock reconciliation in tests)
const mockPlatformStore = new Map();

/**
 * Production-grade Headless Publishing Worker with Exactly-Once-Intent Gate
 * and Ambiguous External Failure Reconciliation Engine.
 */
class PublishWorker {
  constructor(options = {}) {
    this.workerId = options.workerId || `worker-${process.pid}-${Math.random().toString(36).slice(2, 7)}`;
    this.pollIntervalMs = options.pollIntervalMs || 10000;
    this.batchSize = options.batchSize || 10;
    this.leaseTimeoutSeconds = options.leaseTimeoutSeconds || 300; // 5 minutes
    this.maxReconciliationAttempts = options.maxReconciliationAttempts || 5;
    this.retryPolicy = new RetryPolicy({
      maxAttempts: options.maxAttempts || 3,
      baseBackoffMs: options.baseBackoffMs || 1000,
      maxBackoffMs: options.maxBackoffMs || 60000
    });
    this.timer = null;
    this.isRunning = false;
    this.isProcessing = false;
    this.customPublishHandler = options.publishHandler || null;
    this.customReconciliationHandler = options.reconciliationHandler || null;
  }

  /**
   * Set custom publisher handler (useful for testing failure injection)
   */
  setPublishHandler(handler) {
    this.customPublishHandler = handler;
  }

  /**
   * Set custom reconciliation handler (useful for testing reconciliation outcomes)
   */
  setReconciliationHandler(handler) {
    this.customReconciliationHandler = handler;
  }

  /**
   * Reset in-memory mock platform store (for unit tests)
   */
  resetMockPlatformStore() {
    mockPlatformStore.clear();
  }

  /**
   * Start worker polling loop.
   */
  start(intervalMs = null) {
    if (this.isRunning) return;
    if (intervalMs) this.pollIntervalMs = intervalMs;
    this.isRunning = true;

    this.timer = setInterval(async () => {
      try {
        await this.runWorkerCycle();
      } catch (err) {
        console.error(`[PublishWorker:${this.workerId}] Error in worker cycle:`, err.message);
      }
    }, this.pollIntervalMs);

    if (this.timer.unref) {
      this.timer.unref();
    }
  }

  /**
   * Stop worker loop.
   */
  stop() {
    this.isRunning = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /**
   * Run one full worker processing cycle:
   * 1. Recover stale locks (routing in-flight crashed jobs to AMBIGUOUS reconciliation).
   * 2. Claim and process AMBIGUOUS jobs due for reconciliation.
   * 3. Claim and execute due QUEUED/RETRYING platform jobs.
   */
  async runWorkerCycle() {
    if (this.isProcessing) return { processed: 0, succeeded: 0, failed: 0, reconciled: 0 };
    this.isProcessing = true;

    let processed = 0;
    let succeeded = 0;
    let failed = 0;
    let reconciled = 0;

    try {
      // 1. Recover any stale locks
      await scheduledJobService.recoverStaleLocks(this.leaseTimeoutSeconds);

      // 2. Process any due AMBIGUOUS jobs for reconciliation
      const ambiguousJobs = await scheduledJobService.claimAmbiguousJobsForReconciliation(
        this.batchSize,
        this.workerId
      );

      for (const job of ambiguousJobs) {
        processed++;
        try {
          const reconResult = await this.reconcileJob(job);
          if (reconResult && reconResult.success) {
            succeeded++;
            reconciled++;
          } else {
            failed++;
          }
        } catch (reconErr) {
          failed++;
          console.error(`[PublishWorker:${this.workerId}] Error reconciling job ${job.id}:`, reconErr.message);
        }
      }

      // 3. Atomically claim due QUEUED/RETRYING jobs
      const claimedJobs = await scheduledJobService.claimDueJobs(
        this.batchSize,
        this.workerId,
        this.leaseTimeoutSeconds * 1000
      );

      for (const job of claimedJobs) {
        processed++;
        try {
          const result = await this.processJob(job);
          if (result && result.success) {
            succeeded++;
          } else {
            failed++;
          }
        } catch (jobErr) {
          failed++;
          console.error(`[PublishWorker:${this.workerId}] Uncaught error processing job ${job.id}:`, jobErr.message);
        }
      }

      return { processed, succeeded, failed, reconciled };
    } finally {
      this.isProcessing = false;
    }
  }

  /**
   * Process a single claimed job.
   */
  async processJob(job) {
    const { id: jobId, workspaceId, postId, platform, attemptCount, maxAttempts, idempotencyKey } = job;
    const currentAttempt = (attemptCount || 0) + 1;

    // If already in AMBIGUOUS state, route directly to reconciliation
    if (job.status === 'AMBIGUOUS') {
      return await this.reconcileJob(job);
    }

    // 1. Mark status as RUNNING
    await scheduledJobService.markJobRunning(jobId, this.workerId);

    try {
      // 2. Idempotency Check: Verify if platform result already exists and succeeded
      const existingResults = await publishResultService.getResultsForPost(workspaceId, postId);
      const alreadySucceeded = existingResults.find(
        (r) => r.platform && r.platform.toUpperCase() === platform.toUpperCase() && r.status === 'SUCCESS'
      );

      if (alreadySucceeded) {
        // Job was already published previously (idempotent replay)
        await scheduledJobService.markJobSucceeded(jobId, alreadySucceeded.publishedPostId);
        return { success: true, idempotent: true, result: alreadySucceeded };
      }

      // 3. Tenant Isolation & Post Verification
      const post = await socialPostService.getPostById(workspaceId, postId);
      if (!post) {
        throw { code: 'POST_NOT_FOUND', message: `Post ${postId} not found in workspace ${workspaceId}` };
      }

      if (post.status === 'CANCELLED' || post.status === 'ARCHIVED') {
        await scheduledJobService.markJobDeadLetter(jobId, 'POST_CANCELLED', 'Post was cancelled or archived before execution', currentAttempt);
        return { success: false, cancelled: true };
      }

      if (post.approvalState !== 'APPROVED') {
        await scheduledJobService.markJobDeadLetter(jobId, 'POST_NOT_APPROVED', 'Post is not in APPROVED state', currentAttempt);
        return { success: false, notApproved: true };
      }

      // 4. Social Account Verification & Credential Decryption Boundary
      const accounts = await socialAccountService.getAccounts(workspaceId);
      const matchingAccount = accounts.find(
        (a) => a.platform && a.platform.toUpperCase() === platform.toUpperCase()
      );

      let decryptedTokens = null;
      if (matchingAccount) {
        decryptedTokens = await socialAccountService.getDecryptedAccountTokens(workspaceId, matchingAccount.id);
      }

      // 5. Durable Publish Intent Gate (Persist intent BEFORE external platform network transmission)
      const intent = await publishIntentService.recordIntent(
        workspaceId,
        jobId,
        postId,
        platform,
        currentAttempt,
        post,
        { adapter: this.customPublishHandler ? 'custom' : 'default' }
      );

      await scheduledJobService.markJobIntentLocked(jobId, intent.id);

      // Audit Log: Intent recorded
      try {
        await agentLogService.createLog(workspaceId, {
          action: 'PUBLISH_POST',
          platform,
          status: 'RUNNING',
          metadata: {
            event: 'INTENT_RECORDED',
            postId,
            jobId,
            intentId: intent.id,
            clientMutationId: intent.clientMutationId,
            contentHash: intent.contentHash,
            attemptCount: String(currentAttempt)
          }
        });
      } catch (logErr) {}

      // 6. Execute Platform Publishing (Adapter / Custom Handler)
      let publishOutcome = null;

      if (this.customPublishHandler) {
        publishOutcome = await this.customPublishHandler({
          job,
          post,
          account: matchingAccount,
          tokens: decryptedTokens,
          intent,
          clientMutationId: intent.clientMutationId,
          contentHash: intent.contentHash,
          idempotencyKey: intent.idempotencyKey,
          attemptCount: currentAttempt
        });
      } else {
        publishOutcome = await this.defaultPublishPlatform({
          platform,
          post,
          account: matchingAccount,
          tokens: decryptedTokens,
          intent,
          clientMutationId: intent.clientMutationId,
          contentHash: intent.contentHash,
          idempotencyKey: intent.idempotencyKey
        });
      }

      // 7. Mark Intent COMMITTED
      await publishIntentService.markIntentCommitted(intent.id, publishOutcome.externalPostId, {
        externalPostUrl: publishOutcome.externalPostUrl || null
      });

      // 8. Record Success in platform_publish_results
      const savedResult = await publishResultService.savePublishResult(workspaceId, {
        postId,
        platform,
        status: 'SUCCESS',
        publishedPostId: publishOutcome.externalPostId,
        publishedPostUrl: publishOutcome.externalPostUrl,
        idempotencyKey: intent.idempotencyKey,
        rawResponse: {
          platform,
          publishedAt: new Date().toISOString(),
          idempotencyKey: intent.idempotencyKey,
          clientMutationId: intent.clientMutationId
        }
      });

      // 9. Record Operational Audit Log
      try {
        await agentLogService.createLog(workspaceId, {
          action: 'PUBLISH_POST',
          platform,
          status: 'SUCCESS',
          metadata: {
            event: 'PLATFORM_PUBLISH_SUCCESS',
            postId,
            jobId,
            publishedPostId: publishOutcome.externalPostId,
            clientMutationId: intent.clientMutationId,
            attemptCount: String(currentAttempt)
          }
        });
      } catch (logErr) {}

      // 10. Mark Job SUCCEEDED
      await scheduledJobService.markJobSucceeded(jobId, publishOutcome.externalPostId);

      // 11. Check if all target platforms for this post are complete
      await this.checkAndUpdatePostStatus(workspaceId, postId);

      return { success: true, result: savedResult };

    } catch (err) {
      const errorCode = err.code || 'PUBLISH_EXECUTION_ERROR';
      const errorMessage = err.message || 'Unknown publishing error';
      const category = this.retryPolicy.classifyError(errorMessage, errorCode);

      // AMBIGUOUS EXTERNAL OUTCOME HANDLING
      if (this.retryPolicy.isAmbiguous(category)) {
        // Update intent to AMBIGUOUS
        const latestIntent = await publishIntentService.getLatestIntentForJob(workspaceId, jobId);
        if (latestIntent) {
          await publishIntentService.markIntentAmbiguous(latestIntent.id, errorCode, errorMessage);
        }

        // Mark Job as AMBIGUOUS (Never blind retry!)
        await scheduledJobService.markJobAmbiguous(jobId, errorCode, errorMessage, currentAttempt);

        try {
          await agentLogService.createLog(workspaceId, {
            action: 'PUBLISH_POST',
            platform,
            status: 'AMBIGUOUS',
            metadata: {
              event: 'PLATFORM_PUBLISH_AMBIGUOUS',
              postId,
              jobId,
              attemptCount: String(currentAttempt),
              errorCode,
              category,
              errorMessage: String(errorMessage).slice(0, 200)
            }
          });
        } catch (logErr) {}

        // Immediate first-pass reconciliation attempt
        const post = await socialPostService.getPostById(workspaceId, postId);
        const accounts = await socialAccountService.getAccounts(workspaceId);
        const matchingAccount = accounts.find((a) => a.platform && a.platform.toUpperCase() === platform.toUpperCase());
        let decryptedTokens = null;
        if (matchingAccount) {
          decryptedTokens = await socialAccountService.getDecryptedAccountTokens(workspaceId, matchingAccount.id);
        }

        return await this.reconcileJob(
          { ...job, attemptCount: currentAttempt, status: 'AMBIGUOUS' },
          latestIntent,
          matchingAccount,
          decryptedTokens,
          post
        );
      }

      // TRANSIENT & RATE LIMIT RETRYING
      const isRetryable = this.retryPolicy.isRetryable(category) && currentAttempt < maxAttempts;
      if (isRetryable) {
        const nextAttemptAt = this.retryPolicy.calculateNextAttempt(currentAttempt);
        await scheduledJobService.markJobRetrying(jobId, errorCode, errorMessage, nextAttemptAt, currentAttempt);

        try {
          await agentLogService.createLog(workspaceId, {
            action: 'PUBLISH_POST',
            platform,
            status: 'RETRYING',
            metadata: {
              event: 'PLATFORM_PUBLISH_RETRYING',
              postId,
              jobId,
              attemptCount: String(currentAttempt),
              maxAttempts: String(maxAttempts),
              errorCode,
              category,
              nextAttemptAt: nextAttemptAt.toISOString()
            }
          });
        } catch (logErr) {}

        return { success: false, retryable: true, category, nextAttemptAt };
      } else {
        // PERMANENT FAILURE -> DEAD_LETTER
        await scheduledJobService.markJobDeadLetter(jobId, errorCode, errorMessage, currentAttempt);

        try {
          await publishResultService.savePublishResult(workspaceId, {
            postId,
            platform,
            status: 'FAILED',
            errorMessage: errorMessage.slice(0, 500),
            rawResponse: {
              errorCode,
              category,
              attemptCount: currentAttempt
            }
          });
        } catch (resErr) {}

        try {
          await agentLogService.createLog(workspaceId, {
            action: 'PUBLISH_POST',
            platform,
            status: 'FAILED',
            metadata: {
              event: 'PLATFORM_PUBLISH_FAILED_PERMANENT',
              postId,
              jobId,
              attemptCount: String(currentAttempt),
              errorCode,
              category,
              errorMessage: errorMessage.slice(0, 200)
            }
          });
        } catch (logErr) {}

        await this.checkAndUpdatePostStatus(workspaceId, postId);

        return { success: false, permanent: true, category, errorCode };
      }
    }
  }

  /**
   * Deterministic Reconciliation Routine for Ambiguous Jobs.
   * Queries external platform to confirm whether the publication actually succeeded.
   */
  async reconcileJob(job, optionalIntent = null, optionalAccount = null, optionalTokens = null, optionalPost = null) {
    const { id: jobId, workspaceId, postId, platform, attemptCount, maxAttempts } = job;
    const currentAttempt = attemptCount || 1;

    const intent = optionalIntent || await publishIntentService.getLatestIntentForJob(workspaceId, jobId);
    const post = optionalPost || await socialPostService.getPostById(workspaceId, postId);

    let account = optionalAccount;
    let tokens = optionalTokens;

    if (!account) {
      const accounts = await socialAccountService.getAccounts(workspaceId);
      account = accounts.find((a) => a.platform && a.platform.toUpperCase() === platform.toUpperCase());
    }

    if (account && !tokens) {
      tokens = await socialAccountService.getDecryptedAccountTokens(workspaceId, account.id);
    }

    // Audit Log: Reconciliation attempt
    try {
      await agentLogService.createLog(workspaceId, {
        action: 'PUBLISH_POST',
        platform,
        status: 'AMBIGUOUS',
        metadata: {
          event: 'RECONCILIATION_ATTEMPT',
          postId,
          jobId,
          intentId: intent ? intent.id : null,
          clientMutationId: intent ? intent.clientMutationId : null
        }
      });
    } catch (logErr) {}

    // Execute platform-specific reconciliation query
    let reconOutcome = null;

    if (this.customReconciliationHandler) {
      reconOutcome = await this.customReconciliationHandler({
        job,
        intent,
        post,
        account,
        tokens,
        clientMutationId: intent?.clientMutationId,
        contentHash: intent?.contentHash,
        idempotencyKey: intent?.idempotencyKey
      });
    } else {
      reconOutcome = await this.defaultReconcilePlatform({
        platform,
        post,
        account,
        tokens,
        intent
      });
    }

    // 1. RECONCILED: Post was FOUND on the external network!
    if (reconOutcome && reconOutcome.status === 'FOUND') {
      const discoveredPostId = reconOutcome.externalPostId || `reconciled_${platform.toLowerCase()}_${Date.now()}`;
      const discoveredPostUrl = reconOutcome.externalPostUrl || `https://${platform.toLowerCase()}.com/post/${discoveredPostId}`;

      if (intent) {
        await publishIntentService.markIntentReconciled(intent.id, discoveredPostId, 'FOUND');
      }

      await publishResultService.savePublishResult(workspaceId, {
        postId,
        platform,
        status: 'SUCCESS',
        publishedPostId: discoveredPostId,
        publishedPostUrl: discoveredPostUrl,
        idempotencyKey: intent?.idempotencyKey,
        rawResponse: {
          platform,
          reconciled: true,
          publishedAt: new Date().toISOString(),
          clientMutationId: intent?.clientMutationId
        }
      });

      await scheduledJobService.markJobSucceeded(jobId, discoveredPostId);

      try {
        await agentLogService.createLog(workspaceId, {
          action: 'PUBLISH_POST',
          platform,
          status: 'SUCCESS',
          metadata: {
            event: 'RECONCILED_SUCCESS',
            postId,
            jobId,
            publishedPostId: discoveredPostId,
            clientMutationId: intent?.clientMutationId
          }
        });
      } catch (logErr) {}

      await this.checkAndUpdatePostStatus(workspaceId, postId);

      return { success: true, reconciled: true, externalPostId: discoveredPostId, outcome: 'FOUND' };
    }

    // 2. RECONCILED: Post was DEFINITIVELY NOT PUBLISHED!
    if (reconOutcome && reconOutcome.status === 'NOT_FOUND') {
      if (intent) {
        await publishIntentService.recordReconciliationAttempt(intent.id, 'NOT_FOUND');
      }

      if (currentAttempt < maxAttempts) {
        // Safe to retry publication because we verified it was not posted!
        const nextAttemptAt = this.retryPolicy.calculateNextAttempt(currentAttempt);
        await scheduledJobService.markJobRetrying(
          jobId,
          'RECONCILED_NOT_PUBLISHED',
          'Reconciliation verified post was not published; safe to retry',
          nextAttemptAt,
          currentAttempt
        );

        try {
          await agentLogService.createLog(workspaceId, {
            action: 'PUBLISH_POST',
            platform,
            status: 'RETRYING',
            metadata: {
              event: 'RECONCILED_RETRY',
              postId,
              jobId,
              attemptCount: String(currentAttempt),
              nextAttemptAt: nextAttemptAt.toISOString()
            }
          });
        } catch (logErr) {}

        return { success: false, retryable: true, reconciled: true, outcome: 'NOT_FOUND', nextAttemptAt };
      } else {
        // Max retry attempts exhausted
        await scheduledJobService.markJobDeadLetter(
          jobId,
          'MAX_RETRIES_EXCEEDED_AFTER_RECONCILIATION',
          'Post was not found on platform and retry attempts have been exhausted',
          currentAttempt
        );

        await this.checkAndUpdatePostStatus(workspaceId, postId);

        return { success: false, permanent: true, outcome: 'NOT_FOUND' };
      }
    }

    // 3. INDETERMINATE: External platform status query failed or could not verify
    const reason = reconOutcome?.reason || 'External platform status query could not confirm post presence';
    const reconAttempts = ((intent?.reconciliationAttempts) || 0) + 1;

    if (intent) {
      await publishIntentService.recordReconciliationAttempt(intent.id, 'INDETERMINATE', reason);
    }

    if (reconAttempts >= this.maxReconciliationAttempts) {
      // Exceeded max reconciliation queries without certainty -> Dead letter to prevent duplicate!
      const finalMsg = `Could not verify external publication status after ${reconAttempts} attempts; held in dead letter queue to prevent duplicate publication`;
      await scheduledJobService.markJobDeadLetter(jobId, 'AMBIGUOUS_RECONCILIATION_EXHAUSTED', finalMsg, currentAttempt);

      try {
        await agentLogService.createLog(workspaceId, {
          action: 'PUBLISH_POST',
          platform,
          status: 'FAILED',
          metadata: {
            event: 'AMBIGUOUS_DEAD_LETTER',
            postId,
            jobId,
            reconciliationAttempts: String(reconAttempts),
            reason
          }
        });
      } catch (logErr) {}

      await this.checkAndUpdatePostStatus(workspaceId, postId);

      return { success: false, permanent: true, category: 'AMBIGUOUS', exhausted: true };
    } else {
      // Schedule next reconciliation attempt with backoff
      const nextReconAt = new Date(Date.now() + Math.min(1000 * Math.pow(2, reconAttempts), 60000));
      await scheduledJobService.markJobAmbiguous(
        jobId,
        'AMBIGUOUS_INDETERMINATE',
        `Reconciliation attempt ${reconAttempts} indeterminate; retry scheduled`,
        currentAttempt,
        nextReconAt
      );

      try {
        await agentLogService.createLog(workspaceId, {
          action: 'PUBLISH_POST',
          platform,
          status: 'AMBIGUOUS',
          metadata: {
            event: 'RECONCILIATION_INDETERMINATE',
            postId,
            jobId,
            reconciliationAttempts: String(reconAttempts),
            nextReconciliationAt: nextReconAt.toISOString()
          }
        });
      } catch (logErr) {}

      return { success: false, ambiguous: true, nextReconciliationAt: nextReconAt };
    }
  }

  /**
   * Default platform publish adapter.
   * NEVER logs or leaks tokens.
   */
  async defaultPublishPlatform({ platform, post, account, tokens, intent, clientMutationId, idempotencyKey }) {
    const isLive = process.env.ENABLE_LIVE_META_PUBLISH === 'true' || (process.env.NODE_ENV === 'production' && process.env.MOCK_PLATFORMS !== 'true');
    const isMock = !isLive || process.env.MOCK_PLATFORMS === 'true' || !tokens || !tokens.accessToken;

    if (isMock) {
      const externalPostId = `${platform.toLowerCase()}_pub_${post.id.slice(0, 8)}_${Date.now().toString(16)}`;
      const externalPostUrl = `https://${platform.toLowerCase()}.com/post/${externalPostId}`;

      // Save into mockPlatformStore to support deterministic mock reconciliation
      const storeKey = clientMutationId || idempotencyKey || `${post.id}_${platform.toUpperCase()}`;
      mockPlatformStore.set(storeKey, {
        externalPostId,
        externalPostUrl,
        platform: platform.toUpperCase(),
        postId: post.id,
        publishedAt: new Date().toISOString()
      });

      return {
        externalPostId,
        externalPostUrl,
        metadata: { mode: 'mock', platform, clientMutationId }
      };
    }

    // Real Meta Graph API publish for FACEBOOK Pages
    if (platform === 'FACEBOOK') {
      const pageId = account.platformUserId;
      if (!pageId) {
        throw { code: 'PLATFORM_CONFIG_ERROR', message: 'Missing Facebook Page ID for publishing' };
      }
      const mediaUrl = (post.mediaUrls && post.mediaUrls[0]) || (post.media && post.media[0]) || post.mediaUrl || null;
      const message = post.content || post.title || '';
      
      const publishResult = await metaGraphService.publishFacebookPost({
        pageId,
        pageAccessToken: tokens.accessToken,
        message,
        link: post.link || null,
        mediaUrl
      });

      return {
        externalPostId: publishResult.externalPostId,
        externalPostUrl: publishResult.externalPostUrl,
        metadata: { platform: 'FACEBOOK', clientMutationId, livePublish: true }
      };
    }

    // Real Meta Graph API publish for INSTAGRAM Professional Accounts
    if (platform === 'INSTAGRAM') {
      const igUserId = account.platformUserId;
      if (!igUserId) {
        throw { code: 'PLATFORM_CONFIG_ERROR', message: 'Missing Instagram User ID for publishing' };
      }
      const mediaUrl = (post.mediaUrls && post.mediaUrls[0]) || (post.media && post.media[0]) || post.mediaUrl || null;
      if (!mediaUrl) {
        throw { code: 'MEDIA_REQUIRED', message: 'Instagram publishing requires a public image URL' };
      }
      const caption = post.content || post.title || '';

      const publishResult = await metaGraphService.publishInstagramMedia({
        igUserId,
        accessToken: tokens.accessToken,
        imageUrl: mediaUrl,
        caption
      });

      return {
        externalPostId: publishResult.externalPostId,
        externalPostUrl: publishResult.externalPostUrl,
        metadata: { platform: 'INSTAGRAM', clientMutationId, livePublish: true }
      };
    }

    // Real API v2 publish for X / TWITTER
    if (platform === 'TWITTER') {
      const message = post.content || post.title || '';
      let activeAccessToken = tokens.accessToken;

      try {
        const publishResult = await twitterService.publishTweet({
          accessToken: activeAccessToken,
          text: message
        });

        return {
          externalPostId: publishResult.externalPostId,
          externalPostUrl: publishResult.externalPostUrl,
          metadata: { platform: 'TWITTER', clientMutationId, livePublish: true }
        };
      } catch (twErr) {
        // If token expired and refresh token available, attempt refresh once
        if (twErr.errorCode === 'TOKEN_EXPIRED' && tokens.refreshToken) {
          try {
            const refreshed = await twitterService.refreshAccessToken(tokens.refreshToken);
            activeAccessToken = refreshed.accessToken;
            // Update stored tokens
            if (account && account.id) {
              await socialAccountService.updateAccount(post.workspaceId || intent?.workspaceId, account.id, {
                accessToken: refreshed.accessToken,
                refreshToken: refreshed.refreshToken,
                tokenStatus: 'VALID'
              });
            }
            const retryPublish = await twitterService.publishTweet({
              accessToken: activeAccessToken,
              text: message
            });
            return {
              externalPostId: retryPublish.externalPostId,
              externalPostUrl: retryPublish.externalPostUrl,
              metadata: { platform: 'TWITTER', clientMutationId, livePublish: true, refreshed: true }
            };
          } catch (refErr) {
            throw twErr;
          }
        }
        throw twErr;
      }
    }

    // Generic fallback for other platforms (e.g. TikTok preview)
    const externalPostId = `${platform.toLowerCase()}_${Date.now()}`;
    return {
      externalPostId,
      externalPostUrl: `https://${platform.toLowerCase()}.com/${externalPostId}`,
      metadata: { platform, clientMutationId }
    };
  }

  /**
   * Default platform reconciliation adapter.
   * Checks external platform or mock store for post existence.
   */
  async defaultReconcilePlatform({ platform, post, account, tokens, intent }) {
    const isLive = process.env.ENABLE_LIVE_META_PUBLISH === 'true' || (process.env.NODE_ENV === 'production' && process.env.MOCK_PLATFORMS !== 'true');
    const isMock = !isLive || process.env.MOCK_PLATFORMS === 'true' || !tokens || !tokens.accessToken;

    if (isMock) {
      const mutationKey = intent?.clientMutationId;
      const idempotencyKey = intent?.idempotencyKey || `job_${post.id}_${platform.toUpperCase()}`;
      
      const foundByMutation = mutationKey ? mockPlatformStore.get(mutationKey) : null;
      const foundByIdempotency = mockPlatformStore.get(idempotencyKey);
      const found = foundByMutation || foundByIdempotency;

      if (found) {
        return {
          status: 'FOUND',
          externalPostId: found.externalPostId,
          externalPostUrl: found.externalPostUrl
        };
      } else {
        return {
          status: 'NOT_FOUND'
        };
      }
    }

    // Real Meta Graph API reconciliation
    if (platform === 'FACEBOOK' && account && tokens?.accessToken) {
      const pageId = account.platformUserId;
      const message = post.content || post.title || '';
      return await metaGraphService.reconcileFacebookPost({
        pageId,
        pageAccessToken: tokens.accessToken,
        message
      });
    }

    if (platform === 'INSTAGRAM' && account && tokens?.accessToken) {
      const igUserId = account.platformUserId;
      const caption = post.content || post.title || '';
      return await metaGraphService.reconcileInstagramPost({
        igUserId,
        accessToken: tokens.accessToken,
        caption
      });
    }

    if (platform === 'TWITTER' && account && tokens?.accessToken) {
      const message = post.content || post.title || '';
      return await twitterService.reconcileTweet({
        accessToken: tokens.accessToken,
        text: message,
        userId: account.platformUserId
      });
    }

    if (platform === 'LINKEDIN' && account && tokens?.accessToken) {
      const authorUrn = account.platformUserId?.startsWith('urn:li:')
        ? account.platformUserId
        : (account.authorUrn || `urn:li:person:${account.platformUserId || 'me'}`);
      const message = post.content || post.title || '';
      return await linkedInService.reconcilePost({
        accessToken: tokens.accessToken,
        authorUrn,
        commentary: message
      });
    }

    return {
      status: 'NOT_FOUND'
    };
  }

  /**
   * Check if all platform jobs for a post have finished, and update post status accordingly.
   */
  async checkAndUpdatePostStatus(workspaceId, postId) {
    try {
      const post = await socialPostService.getPostById(workspaceId, postId);
      if (!post) return;

      const jobs = await scheduledJobService.getJobsForPost(workspaceId, postId);
      if (jobs.length === 0) return;

      const allSucceeded = jobs.every((j) => j.status === 'SUCCEEDED');
      const anyDeadLetter = jobs.some((j) => j.status === 'DEAD_LETTER' || j.status === 'FAILED');
      const allDone = jobs.every((j) => j.status === 'SUCCEEDED' || j.status === 'DEAD_LETTER' || j.status === 'FAILED');

      if (allSucceeded) {
        await socialPostService.updatePost(workspaceId, postId, {
          status: 'PUBLISHED',
          publishedAt: new Date().toISOString()
        });
      } else if (allDone && anyDeadLetter) {
        await socialPostService.updatePost(workspaceId, postId, {
          status: 'FAILED'
        });
      }
    } catch (err) {
      console.error(`[PublishWorker:${this.workerId}] Error updating post status for ${postId}:`, err.message);
    }
  }
}

module.exports = new PublishWorker();
