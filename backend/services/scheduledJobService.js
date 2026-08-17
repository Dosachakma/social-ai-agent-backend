const crypto = require('crypto');
const db = require('../db/pool');

const memoryJobStore = new Map();

/**
 * Service for managing durable scheduled platform jobs.
 * Enforces PostgreSQL atomic locking (SELECT ... FOR UPDATE SKIP LOCKED)
 * and strict tenant isolation.
 */
class ScheduledJobService {
  /**
   * Reset in-memory store (for unit tests)
   */
  resetMemoryStore() {
    memoryJobStore.clear();
  }

  /**
   * Create platform jobs for a scheduled post.
   * Uses database UNIQUE (post_id, platform) to guarantee idempotency.
   */
  async createJobsForPost(workspaceId, postId, platforms, scheduledAt, maxAttempts = 3) {
    if (!workspaceId || !postId || !Array.isArray(platforms) || platforms.length === 0) {
      return [];
    }

    const nextAttemptAt = scheduledAt ? new Date(scheduledAt) : new Date();
    const createdJobs = [];

    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot create scheduled jobs in production without database.');
      }
      for (const rawPlatform of platforms) {
        const platform = rawPlatform.toUpperCase();
        const idempotencyKey = `job_${postId}_${platform}`;

        // Check if job already exists for this post & platform
        let existing = null;
        for (const j of memoryJobStore.values()) {
          if (j.postId === postId && j.platform === platform) {
            existing = j;
            break;
          }
        }

        if (!existing) {
          const id = 'job-' + postId.slice(0, 8) + '-' + platform.toLowerCase() + '-' + crypto.randomUUID().slice(0, 8);
          const job = {
            id,
            workspaceId,
            postId,
            platform,
            status: 'QUEUED',
            attemptCount: 0,
            maxAttempts,
            nextAttemptAt: nextAttemptAt.toISOString(),
            lockedAt: null,
            lockedBy: null,
            startedAt: null,
            completedAt: null,
            lastErrorCode: null,
            lastErrorMessage: null,
            idempotencyKey,
            payload: {},
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
          };
          memoryJobStore.set(id, job);
          createdJobs.push({ ...job });
        } else {
          createdJobs.push({ ...existing });
        }
      }
      return createdJobs;
    }

    for (const rawPlatform of platforms) {
      const platform = rawPlatform.toUpperCase();
      const idempotencyKey = `job_${postId}_${platform}`;

      const queryText = `
        INSERT INTO scheduled_publish_jobs (
          workspace_id,
          post_id,
          platform,
          status,
          attempt_count,
          max_attempts,
          next_attempt_at,
          idempotency_key,
          payload,
          created_at,
          updated_at
        ) VALUES ($1, $2, $3, 'QUEUED', 0, $4, $5, $6, '{}'::jsonb, NOW(), NOW())
        ON CONFLICT (post_id, platform) DO UPDATE SET
          next_attempt_at = CASE 
            WHEN scheduled_publish_jobs.status IN ('QUEUED', 'RETRYING') THEN EXCLUDED.next_attempt_at 
            ELSE scheduled_publish_jobs.next_attempt_at 
          END,
          updated_at = NOW()
        RETURNING 
          id,
          workspace_id AS "workspaceId",
          post_id AS "postId",
          platform,
          status,
          attempt_count AS "attemptCount",
          max_attempts AS "maxAttempts",
          next_attempt_at AS "nextAttemptAt",
          locked_at AS "lockedAt",
          locked_by AS "lockedBy",
          started_at AS "startedAt",
          completed_at AS "completedAt",
          last_error_code AS "lastErrorCode",
          last_error_message AS "lastErrorMessage",
          idempotency_key AS "idempotencyKey",
          payload,
          created_at AS "createdAt",
          updated_at AS "updatedAt";
      `;

      const result = await db.query(queryText, [
        workspaceId,
        postId,
        platform,
        maxAttempts,
        nextAttemptAt,
        idempotencyKey
      ]);

      if (result.rows[0]) {
        createdJobs.push(result.rows[0]);
      }
    }

    return createdJobs;
  }

  /**
   * Find approved scheduled posts whose scheduled_at <= NOW() that need platform jobs.
   */
  async findDuePostsForDispatch(limit = 50) {
    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot query due posts in production without database.');
      }
      const socialPostService = require('./socialPostService');
      return await socialPostService.findDuePosts(limit);
    }

    const queryText = `
      SELECT 
        p.id,
        p.workspace_id AS "workspaceId",
        p.created_by_user_id AS "createdByUserId",
        p.title,
        p.content,
        p.content AS "caption",
        p.target_platforms AS "targetPlatforms",
        p.status,
        p.approval_state AS "approvalState",
        p.scheduled_at AS "scheduledAt",
        p.scheduled_time AS "scheduledTime",
        p.timezone,
        p.repeat_option AS "repeatOption",
        p.require_approval AS "requireApproval",
        p.media_urls AS "mediaUrls",
        p.hashtags,
        p.cta,
        p.is_ai_generated AS "isAiGenerated",
        p.max_retries AS "maxRetries"
      FROM social_posts p
      WHERE p.status = 'SCHEDULED'
        AND p.approval_state = 'APPROVED'
        AND p.scheduled_at IS NOT NULL
        AND p.scheduled_at <= NOW()
      ORDER BY p.scheduled_at ASC
      LIMIT $1;
    `;

    const result = await db.query(queryText, [limit]);
    return result.rows;
  }

  /**
   * Atomically claim due platform jobs using SELECT ... FOR UPDATE SKIP LOCKED.
   * Ensures two concurrent workers never claim the same job.
   */
  async claimDueJobs(batchSize = 10, workerId = 'worker-1', leaseTimeoutMs = 300000) {
    const effectiveWorkerId = workerId || `worker-${Date.now()}`;

    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot claim jobs in production without database.');
      }
      const now = Date.now();
      const claimed = [];
      for (const job of memoryJobStore.values()) {
        if (claimed.length >= batchSize) break;
        const isDue = job.nextAttemptAt && new Date(job.nextAttemptAt).getTime() <= now;
        const isQueued = job.status === 'QUEUED' || job.status === 'RETRYING';
        if (isDue && isQueued) {
          job.status = 'CLAIMED';
          job.lockedAt = new Date().toISOString();
          job.lockedBy = effectiveWorkerId;
          job.startedAt = job.startedAt || new Date().toISOString();
          job.updatedAt = new Date().toISOString();
          claimed.push({ ...job });
        }
      }
      return claimed;
    }

    // Atomic CTE: Lock rows with FOR UPDATE SKIP LOCKED, then UPDATE status to CLAIMED
    const queryText = `
      WITH candidate_jobs AS (
        SELECT id
        FROM scheduled_publish_jobs
        WHERE status IN ('QUEUED', 'RETRYING')
          AND next_attempt_at <= NOW()
        ORDER BY next_attempt_at ASC
        LIMIT $1
        FOR UPDATE SKIP LOCKED
      )
      UPDATE scheduled_publish_jobs j
      SET 
        status = 'CLAIMED',
        locked_at = NOW(),
        locked_by = $2,
        started_at = COALESCE(j.started_at, NOW()),
        updated_at = NOW()
      FROM candidate_jobs c
      WHERE j.id = c.id
      RETURNING 
        j.id,
        j.workspace_id AS "workspaceId",
        j.post_id AS "postId",
        j.platform,
        j.status,
        j.attempt_count AS "attemptCount",
        j.max_attempts AS "maxAttempts",
        j.next_attempt_at AS "nextAttemptAt",
        j.locked_at AS "lockedAt",
        j.locked_by AS "lockedBy",
        j.started_at AS "startedAt",
        j.completed_at AS "completedAt",
        j.last_error_code AS "lastErrorCode",
        j.last_error_message AS "lastErrorMessage",
        j.idempotency_key AS "idempotencyKey",
        j.payload,
        j.created_at AS "createdAt",
        j.updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [batchSize, effectiveWorkerId]);
    return result.rows;
  }

  /**
   * Mark job as actively RUNNING.
   */
  async markJobRunning(jobId, workerId) {
    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'RUNNING';
        job.lockedBy = workerId;
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'RUNNING',
        locked_by = $2,
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [jobId, workerId]);
    return result.rows[0] || null;
  }

  /**
   * Mark job as INTENT_LOCKED (Durable Publish Intent recorded before external network call).
   */
  async markJobIntentLocked(jobId, intentId) {
    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'INTENT_LOCKED';
        job.payload = { ...job.payload, activeIntentId: intentId };
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'INTENT_LOCKED',
        payload = payload || jsonb_build_object('activeIntentId', $2::text),
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, payload, updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [jobId, intentId]);
    return result.rows[0] || null;
  }

  /**
   * Mark job as AMBIGUOUS (Unconfirmed external outcome requiring deterministic reconciliation).
   */
  async markJobAmbiguous(jobId, errorCode, errorMessage, currentAttempt, nextReconciliationAt = null) {
    const sanitizedError = errorMessage ? String(errorMessage).slice(0, 1000) : null;
    const nextAt = nextReconciliationAt ? new Date(nextReconciliationAt) : new Date(Date.now() + 5000);

    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'AMBIGUOUS';
        job.attemptCount = currentAttempt;
        job.nextAttemptAt = nextAt.toISOString();
        job.lockedAt = null;
        job.lockedBy = null;
        job.lastErrorCode = errorCode || 'AMBIGUOUS_EXTERNAL_OUTCOME';
        job.lastErrorMessage = sanitizedError;
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'AMBIGUOUS',
        attempt_count = $2,
        next_attempt_at = $3,
        locked_at = NULL,
        locked_by = NULL,
        last_error_code = $4,
        last_error_message = $5,
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, attempt_count AS "attemptCount", next_attempt_at AS "nextAttemptAt";
    `;

    const result = await db.query(queryText, [
      jobId,
      currentAttempt,
      nextAt,
      errorCode || 'AMBIGUOUS_EXTERNAL_OUTCOME',
      sanitizedError
    ]);
    return result.rows[0] || null;
  }

  /**
   * Atomically claim ambiguous jobs that are due for reconciliation.
   */
  async claimAmbiguousJobsForReconciliation(batchSize = 10, workerId = 'reconciler-1') {
    const effectiveWorkerId = workerId || `reconciler-${Date.now()}`;

    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot claim ambiguous jobs in production without database.');
      }
      const now = Date.now();
      const claimed = [];
      for (const job of memoryJobStore.values()) {
        if (claimed.length >= batchSize) break;
        const isDue = job.nextAttemptAt && new Date(job.nextAttemptAt).getTime() <= now;
        if (job.status === 'AMBIGUOUS' && isDue) {
          job.status = 'CLAIMED';
          job.lockedAt = new Date().toISOString();
          job.lockedBy = effectiveWorkerId;
          job.updatedAt = new Date().toISOString();
          claimed.push({ ...job });
        }
      }
      return claimed;
    }

    const queryText = `
      WITH candidate_jobs AS (
        SELECT id
        FROM scheduled_publish_jobs
        WHERE status = 'AMBIGUOUS'
          AND next_attempt_at <= NOW()
        ORDER BY next_attempt_at ASC
        LIMIT $1
        FOR UPDATE SKIP LOCKED
      )
      UPDATE scheduled_publish_jobs j
      SET 
        status = 'CLAIMED',
        locked_at = NOW(),
        locked_by = $2,
        updated_at = NOW()
      FROM candidate_jobs c
      WHERE j.id = c.id
      RETURNING 
        j.id,
        j.workspace_id AS "workspaceId",
        j.post_id AS "postId",
        j.platform,
        j.status,
        j.attempt_count AS "attemptCount",
        j.max_attempts AS "maxAttempts",
        j.next_attempt_at AS "nextAttemptAt",
        j.locked_at AS "lockedAt",
        j.locked_by AS "lockedBy",
        j.started_at AS "startedAt",
        j.completed_at AS "completedAt",
        j.last_error_code AS "lastErrorCode",
        j.last_error_message AS "lastErrorMessage",
        j.idempotency_key AS "idempotencyKey",
        j.payload,
        j.created_at AS "createdAt",
        j.updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [batchSize, effectiveWorkerId]);
    return result.rows;
  }

  /**
   * Mark job as successfully completed (SUCCEEDED).
   */
  async markJobSucceeded(jobId, externalPostId = null) {
    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'SUCCEEDED';
        job.completedAt = new Date().toISOString();
        job.lockedAt = null;
        job.lockedBy = null;
        job.lastErrorCode = null;
        job.lastErrorMessage = null;
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'SUCCEEDED',
        completed_at = NOW(),
        locked_at = NULL,
        locked_by = NULL,
        last_error_code = NULL,
        last_error_message = NULL,
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, completed_at AS "completedAt";
    `;

    const result = await db.query(queryText, [jobId]);
    return result.rows[0] || null;
  }

  /**
   * Mark job for RETRYING with next_attempt_at backoff timestamp.
   */
  async markJobRetrying(jobId, errorCode, errorMessage, nextAttemptAt, attemptCount) {
    const sanitizedError = errorMessage ? String(errorMessage).slice(0, 1000) : null;

    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'RETRYING';
        job.attemptCount = attemptCount;
        job.nextAttemptAt = (nextAttemptAt || new Date()).toISOString();
        job.lockedAt = null;
        job.lockedBy = null;
        job.lastErrorCode = errorCode;
        job.lastErrorMessage = sanitizedError;
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'RETRYING',
        attempt_count = $2,
        next_attempt_at = $3,
        locked_at = NULL,
        locked_by = NULL,
        last_error_code = $4,
        last_error_message = $5,
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, attempt_count AS "attemptCount", next_attempt_at AS "nextAttemptAt";
    `;

    const result = await db.query(queryText, [
      jobId,
      attemptCount,
      nextAttemptAt || new Date(),
      errorCode,
      sanitizedError
    ]);
    return result.rows[0] || null;
  }

  /**
   * Mark job as permanently failed (DEAD_LETTER / FAILED).
   */
  async markJobDeadLetter(jobId, errorCode, errorMessage, attemptCount) {
    const sanitizedError = errorMessage ? String(errorMessage).slice(0, 1000) : null;

    if (!db.isConfigured()) {
      const job = memoryJobStore.get(jobId);
      if (job) {
        job.status = 'DEAD_LETTER';
        job.attemptCount = attemptCount;
        job.completedAt = new Date().toISOString();
        job.lockedAt = null;
        job.lockedBy = null;
        job.lastErrorCode = errorCode;
        job.lastErrorMessage = sanitizedError;
        job.updatedAt = new Date().toISOString();
        return { ...job };
      }
      return null;
    }

    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = 'DEAD_LETTER',
        attempt_count = $2,
        completed_at = NOW(),
        locked_at = NULL,
        locked_by = NULL,
        last_error_code = $3,
        last_error_message = $4,
        updated_at = NOW()
      WHERE id = $1
      RETURNING id, status, completed_at AS "completedAt";
    `;

    const result = await db.query(queryText, [
      jobId,
      attemptCount,
      errorCode,
      sanitizedError
    ]);
    return result.rows[0] || null;
  }

  /**
   * Reclaim stale locks when a worker crashed or exceeded its lease timeout.
   * Differentiates pre-execution claims (safe to retry) from in-flight/running jobs
   * (which must be routed to AMBIGUOUS reconciliation to prevent duplicate external publications).
   */
  async recoverStaleLocks(leaseTimeoutSeconds = 300) {
    if (!db.isConfigured()) {
      if (process.env.NODE_ENV === 'production') {
        throw new Error('DATABASE_UNAVAILABLE: Cannot recover stale locks in production without database.');
      }
      const cutoff = Date.now() - (leaseTimeoutSeconds * 1000);
      const recovered = [];
      for (const job of memoryJobStore.values()) {
        if ((job.status === 'CLAIMED' || job.status === 'RUNNING' || job.status === 'INTENT_LOCKED') && job.lockedAt) {
          if (new Date(job.lockedAt).getTime() < cutoff) {
            if (job.status === 'RUNNING' || job.status === 'INTENT_LOCKED') {
              // In-flight crash -> AMBIGUOUS (must reconcile)
              job.status = 'AMBIGUOUS';
              job.lockedAt = null;
              job.lockedBy = null;
              job.nextAttemptAt = new Date().toISOString();
              job.lastErrorCode = 'CRASH_IN_FLIGHT_LEASE_EXPIRED';
              job.lastErrorMessage = 'Worker lease expired while execution in-flight; routed to reconciliation';
            } else {
              // Pre-execution claim -> RETRYING
              job.status = 'RETRYING';
              job.lockedAt = null;
              job.lockedBy = null;
              job.nextAttemptAt = new Date().toISOString();
              job.lastErrorCode = 'STALE_LOCK_RECOVERED';
              job.lastErrorMessage = 'Worker lease expired; reclaimed for retry';
            }
            job.updatedAt = new Date().toISOString();
            recovered.push({ ...job });
          }
        }
      }
      return recovered;
    }

    // In SQL: Use CASE statement to transition in-flight running jobs to AMBIGUOUS and claimed jobs to RETRYING
    const queryText = `
      UPDATE scheduled_publish_jobs
      SET 
        status = CASE 
          WHEN status IN ('RUNNING', 'INTENT_LOCKED') THEN 'AMBIGUOUS' 
          ELSE 'RETRYING' 
        END,
        locked_at = NULL,
        locked_by = NULL,
        next_attempt_at = NOW(),
        last_error_code = CASE 
          WHEN status IN ('RUNNING', 'INTENT_LOCKED') THEN 'CRASH_IN_FLIGHT_LEASE_EXPIRED' 
          ELSE 'STALE_LOCK_RECOVERED' 
        END,
        last_error_message = CASE 
          WHEN status IN ('RUNNING', 'INTENT_LOCKED') THEN 'Worker lease expired while execution in-flight; routed to reconciliation' 
          ELSE 'Worker lease expired; reclaimed for retry' 
        END,
        updated_at = NOW()
      WHERE status IN ('CLAIMED', 'RUNNING', 'INTENT_LOCKED')
        AND locked_at < NOW() - ($1 * INTERVAL '1 second')
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        post_id AS "postId",
        platform,
        status,
        last_error_code AS "lastErrorCode";
    `;

    const result = await db.query(queryText, [leaseTimeoutSeconds]);
    return result.rows;
  }

  /**
   * Get jobs for a specific post within a workspace.
   */
  async getJobsForPost(workspaceId, postId) {
    if (!workspaceId || !postId) return [];

    if (!db.isConfigured()) {
      const list = [];
      for (const j of memoryJobStore.values()) {
        if (j.workspaceId === workspaceId && j.postId === postId) {
          list.push({ ...j });
        }
      }
      return list;
    }

    const queryText = `
      SELECT 
        id,
        workspace_id AS "workspaceId",
        post_id AS "postId",
        platform,
        status,
        attempt_count AS "attemptCount",
        max_attempts AS "maxAttempts",
        next_attempt_at AS "nextAttemptAt",
        locked_at AS "lockedAt",
        locked_by AS "lockedBy",
        started_at AS "startedAt",
        completed_at AS "completedAt",
        last_error_code AS "lastErrorCode",
        last_error_message AS "lastErrorMessage",
        idempotency_key AS "idempotencyKey",
        payload,
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM scheduled_publish_jobs
      WHERE workspace_id = $1 AND post_id = $2
      ORDER BY created_at ASC;
    `;

    const result = await db.query(queryText, [workspaceId, postId]);
    return result.rows;
  }

  /**
   * Get queue health metrics (backlog, running, retry, dead letter, ambiguous counts)
   */
  async getQueueMetrics() {
    if (!db.isConfigured()) {
      let queued = 0, running = 0, retrying = 0, succeeded = 0, deadLetter = 0, ambiguous = 0;
      for (const j of memoryJobStore.values()) {
        if (j.status === 'QUEUED') queued++;
        else if (j.status === 'CLAIMED' || j.status === 'RUNNING' || j.status === 'INTENT_LOCKED') running++;
        else if (j.status === 'RETRYING') retrying++;
        else if (j.status === 'SUCCEEDED') succeeded++;
        else if (j.status === 'DEAD_LETTER' || j.status === 'FAILED') deadLetter++;
        else if (j.status === 'AMBIGUOUS') ambiguous++;
      }
      return { queued, running, retrying, succeeded, deadLetter, ambiguous, total: memoryJobStore.size };
    }

    const queryText = `
      SELECT 
        COUNT(*) FILTER (WHERE status = 'QUEUED') AS queued,
        COUNT(*) FILTER (WHERE status IN ('CLAIMED', 'RUNNING', 'INTENT_LOCKED')) AS running,
        COUNT(*) FILTER (WHERE status = 'RETRYING') AS retrying,
        COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
        COUNT(*) FILTER (WHERE status IN ('DEAD_LETTER', 'FAILED')) AS "deadLetter",
        COUNT(*) FILTER (WHERE status = 'AMBIGUOUS') AS ambiguous,
        COUNT(*) AS total
      FROM scheduled_publish_jobs;
    `;

    const result = await db.query(queryText);
    const row = result.rows[0] || {};
    return {
      queued: parseInt(row.queued || 0, 10),
      running: parseInt(row.running || 0, 10),
      retrying: parseInt(row.retrying || 0, 10),
      succeeded: parseInt(row.succeeded || 0, 10),
      deadLetter: parseInt(row.deadLetter || 0, 10),
      ambiguous: parseInt(row.ambiguous || 0, 10),
      total: parseInt(row.total || 0, 10)
    };
  }

  /**
   * Reset in-memory job store for clean test isolation.
   */
  resetMemoryStore() {
    memoryJobStore.clear();
  }

  /**
   * Test helper to set lockedAt for testing stale recovery in non-db mode.
   */
  _setMemoryJobLockedAt(jobId, dateIso) {
    const job = memoryJobStore.get(jobId);
    if (job) {
      job.lockedAt = dateIso;
    }
  }
}

module.exports = new ScheduledJobService();
