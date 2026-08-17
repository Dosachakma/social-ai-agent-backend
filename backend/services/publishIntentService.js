const crypto = require('crypto');
const db = require('../db/pool');

const memoryIntentStore = new Map();

/**
 * Service for durable Publish Intent Records (Exactly-Once-Intent Gate).
 * Records intent with cryptographic content hashing and client mutation ID
 * before external network transmission, and tracks reconciliation states.
 */
class PublishIntentService {
  /**
   * Reset in-memory intent store (for tests)
   */
  resetMemoryStore() {
    memoryIntentStore.clear();
  }

  /**
   * Compute deterministic SHA-256 hash of canonical post payload.
   * @param {Object} post
   * @returns {string} SHA-256 hex string (64 characters)
   */
  computeContentHash(post) {
    if (!post) return crypto.createHash('sha256').update('').digest('hex');
    const canonical = {
      title: post.title || '',
      content: post.content || post.caption || '',
      targetPlatforms: Array.isArray(post.targetPlatforms)
        ? [...post.targetPlatforms].sort()
        : (typeof post.targetPlatforms === 'string' ? JSON.parse(post.targetPlatforms).sort() : []),
      mediaUrls: Array.isArray(post.mediaUrls)
        ? [...post.mediaUrls].sort()
        : (Array.isArray(post.media) ? [...post.media].sort() : []),
      scheduledAt: post.scheduledAt || post.scheduledTime || '',
      timezone: post.timezone || 'UTC'
    };
    return crypto.createHash('sha256').update(JSON.stringify(canonical)).digest('hex');
  }

  /**
   * Generate deterministic client mutation ID for external platform idempotency.
   */
  generateClientMutationId(workspaceId, postId, platform, attemptNumber, contentHash) {
    if (arguments.length === 3) {
      const jobId = workspaceId;
      const attempt = postId;
      const hash = platform;
      const raw = `intent:job:${jobId}:${attempt}:${hash}`;
      return crypto.createHash('sha256').update(raw).digest('hex');
    }
    const raw = `intent:${workspaceId}:${postId}:${(platform || '').toUpperCase()}:${attemptNumber}:${contentHash}`;
    return crypto.createHash('sha256').update(raw).digest('hex');
  }

  /**
   * Record a publish intent BEFORE making external platform call.
   * Durably sets state to 'IN_FLIGHT' and records sent_at.
   */
  async recordIntent(workspaceId, jobId, postId, platform, attemptNumber, post, extraMetadata = {}) {
    const contentHash = this.computeContentHash(post);
    const clientMutationId = this.generateClientMutationId(workspaceId, postId, platform, attemptNumber, contentHash);
    const idempotencyKey = `job_${postId}_${platform.toUpperCase()}`;
    const sentAt = new Date().toISOString();

    // Sanitize metadata to guarantee ZERO token leakage
    const sanitizedMetadata = {
      platformAdapter: extraMetadata.adapter || 'default',
      contentLength: (post.content || post.caption || '').length,
      hasMedia: Boolean(post.mediaUrls?.length || post.media?.length || post.mediaUrl),
      clientMutationId,
      attemptNumber
    };

    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot record publish intent in production without database.');
      }
      const id = 'intent-' + jobId.slice(0, 8) + '-' + attemptNumber + '-' + crypto.randomUUID().slice(0, 8);
      const key = `${jobId}_${attemptNumber}`;
      const intentRecord = {
        id,
        jobId,
        workspaceId,
        postId,
        platform: platform.toUpperCase(),
        clientMutationId,
        idempotencyKey,
        contentHash,
        state: 'IN_FLIGHT',
        attemptNumber,
        sentAt,
        responseReceivedAt: null,
        reconciliationAttempts: 0,
        lastReconciledAt: null,
        externalPostId: null,
        metadata: sanitizedMetadata,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      memoryIntentStore.set(key, intentRecord);
      return { ...intentRecord };
    }

    const queryText = `
      INSERT INTO publish_intents (
        job_id,
        workspace_id,
        post_id,
        platform,
        client_mutation_id,
        idempotency_key,
        content_hash,
        state,
        attempt_number,
        sent_at,
        metadata,
        created_at,
        updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, 'IN_FLIGHT', $8, NOW(), $9, NOW(), NOW())
      ON CONFLICT (job_id, attempt_number) DO UPDATE SET
        state = 'IN_FLIGHT',
        sent_at = NOW(),
        metadata = EXCLUDED.metadata,
        updated_at = NOW()
      RETURNING 
        id,
        job_id AS "jobId",
        workspace_id AS "workspaceId",
        post_id AS "postId",
        platform,
        client_mutation_id AS "clientMutationId",
        idempotency_key AS "idempotencyKey",
        content_hash AS "contentHash",
        state,
        attempt_number AS "attemptNumber",
        sent_at AS "sentAt",
        response_received_at AS "responseReceivedAt",
        reconciliation_attempts AS "reconciliationAttempts",
        last_reconciled_at AS "lastReconciledAt",
        external_post_id AS "externalPostId",
        metadata,
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      jobId,
      workspaceId,
      postId,
      platform.toUpperCase(),
      clientMutationId,
      idempotencyKey,
      contentHash,
      attemptNumber,
      JSON.stringify(sanitizedMetadata)
    ]);

    return result.rows[0];
  }

  /**
   * Mark intent as successfully committed after external platform responded with success.
   */
  async markIntentCommitted(intentId, externalPostId, metadata = {}) {
    const receivedAt = new Date().toISOString();

    if (!db.isConfigured()) {
      for (const intent of memoryIntentStore.values()) {
        if (intent.id === intentId) {
          intent.state = 'COMMITTED';
          intent.externalPostId = externalPostId;
          intent.responseReceivedAt = receivedAt;
          intent.metadata = { ...intent.metadata, ...metadata };
          intent.updatedAt = new Date().toISOString();
          return { ...intent };
        }
      }
      return null;
    }

    const queryText = `
      UPDATE publish_intents
      SET 
        state = 'COMMITTED',
        external_post_id = $2,
        response_received_at = NOW(),
        metadata = metadata || $3::jsonb,
        updated_at = NOW()
      WHERE id = $1
      RETURNING 
        id,
        job_id AS "jobId",
        workspace_id AS "workspaceId",
        post_id AS "postId",
        platform,
        client_mutation_id AS "clientMutationId",
        state,
        external_post_id AS "externalPostId",
        response_received_at AS "responseReceivedAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [intentId, externalPostId, JSON.stringify(metadata)]);
    return result.rows[0] || null;
  }

  /**
   * Mark intent as AMBIGUOUS when network connection drops or times out during execution.
   */
  async markIntentAmbiguous(intentId, errorCode, errorMessage) {
    if (!db.isConfigured()) {
      for (const intent of memoryIntentStore.values()) {
        if (intent.id === intentId) {
          intent.state = 'AMBIGUOUS';
          intent.metadata = {
            ...intent.metadata,
            ambiguousErrorCode: errorCode,
            ambiguousErrorMessage: errorMessage ? String(errorMessage).slice(0, 500) : null
          };
          intent.updatedAt = new Date().toISOString();
          return { ...intent };
        }
      }
      return null;
    }

    const queryText = `
      UPDATE publish_intents
      SET 
        state = 'AMBIGUOUS',
        metadata = metadata || jsonb_build_object(
          'ambiguousErrorCode', $2::text,
          'ambiguousErrorMessage', $3::text
        ),
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, state, updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      intentId,
      errorCode || 'UNKNOWN_AMBIGUOUS_ERROR',
      errorMessage ? String(errorMessage).slice(0, 500) : null
    ]);
    return result.rows[0] || null;
  }

  /**
   * Mark intent as successfully RECONCILED.
   */
  async markIntentReconciled(intentId, externalPostId, outcome) {
    if (!db.isConfigured()) {
      for (const intent of memoryIntentStore.values()) {
        if (intent.id === intentId) {
          intent.state = 'RECONCILED';
          intent.externalPostId = externalPostId || intent.externalPostId;
          intent.reconciliationAttempts = (intent.reconciliationAttempts || 0) + 1;
          intent.lastReconciledAt = new Date().toISOString();
          intent.metadata = { ...intent.metadata, reconciliationOutcome: outcome };
          intent.updatedAt = new Date().toISOString();
          return { ...intent };
        }
      }
      return null;
    }

    const queryText = `
      UPDATE publish_intents
      SET 
        state = 'RECONCILED',
        external_post_id = COALESCE($2, external_post_id),
        reconciliation_attempts = reconciliation_attempts + 1,
        last_reconciled_at = NOW(),
        metadata = metadata || jsonb_build_object('reconciliationOutcome', $3::text),
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, state, external_post_id AS "externalPostId", reconciliation_attempts AS "reconciliationAttempts";
    `;

    const result = await db.query(queryText, [intentId, externalPostId || null, outcome || 'FOUND']);
    return result.rows[0] || null;
  }

  /**
   * Record a failed or indeterminate reconciliation attempt increment.
   */
  async recordReconciliationAttempt(intentId, outcome, errorDetails = null) {
    if (!db.isConfigured()) {
      for (const intent of memoryIntentStore.values()) {
        if (intent.id === intentId) {
          intent.reconciliationAttempts = (intent.reconciliationAttempts || 0) + 1;
          intent.lastReconciledAt = new Date().toISOString();
          intent.metadata = {
            ...intent.metadata,
            lastReconciliationOutcome: outcome,
            lastReconciliationError: errorDetails ? String(errorDetails).slice(0, 500) : null
          };
          intent.updatedAt = new Date().toISOString();
          return { ...intent };
        }
      }
      return null;
    }

    const queryText = `
      UPDATE publish_intents
      SET 
        reconciliation_attempts = reconciliation_attempts + 1,
        last_reconciled_at = NOW(),
        metadata = metadata || jsonb_build_object(
          'lastReconciliationOutcome', $2::text,
          'lastReconciliationError', $3::text
        ),
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, reconciliation_attempts AS "reconciliationAttempts", last_reconciled_at AS "lastReconciledAt";
    `;

    const result = await db.query(queryText, [
      intentId,
      outcome || 'INDETERMINATE',
      errorDetails ? String(errorDetails).slice(0, 500) : null
    ]);
    return result.rows[0] || null;
  }

  /**
   * Get latest intent record for a job.
   */
  async getLatestIntentForJob(workspaceId, jobId) {
    if (!workspaceId || !jobId) return null;

    if (!db.isConfigured()) {
      let latest = null;
      for (const intent of memoryIntentStore.values()) {
        if (intent.workspaceId === workspaceId && intent.jobId === jobId) {
          if (!latest || intent.attemptNumber > latest.attemptNumber) {
            latest = intent;
          }
        }
      }
      return latest ? { ...latest } : null;
    }

    const queryText = `
      SELECT 
        id,
        job_id AS "jobId",
        workspace_id AS "workspaceId",
        post_id AS "postId",
        platform,
        client_mutation_id AS "clientMutationId",
        idempotency_key AS "idempotencyKey",
        content_hash AS "contentHash",
        state,
        attempt_number AS "attemptNumber",
        sent_at AS "sentAt",
        response_received_at AS "responseReceivedAt",
        reconciliation_attempts AS "reconciliationAttempts",
        last_reconciled_at AS "lastReconciledAt",
        external_post_id AS "externalPostId",
        metadata,
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM publish_intents
      WHERE workspace_id = $1 AND job_id = $2
      ORDER BY attempt_number DESC
      LIMIT 1;
    `;

    const result = await db.query(queryText, [workspaceId, jobId]);
    return result.rows[0] || null;
  }
}

module.exports = new PublishIntentService();
