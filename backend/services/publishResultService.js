const db = require('../db/pool');

const memoryStore = new Map();

/**
 * Service for tenant-isolated Platform Publish Results.
 */
class PublishResultService {
  /**
   * List publish results for a specific post within a workspace.
   */
  async getPublishResultsForPost(workspaceId, postId) {
    if (!workspaceId || !postId) return [];

    if (!db.isConfigured()) {
      const list = [];
      for (const res of memoryStore.values()) {
        if (res.workspaceId === workspaceId && res.postId === postId) {
          list.push({ ...res });
        }
      }
      return list;
    }

    const queryText = `
      SELECT 
        pr.id,
        pr.post_id AS "postId",
        pr.workspace_id AS "workspaceId",
        pr.platform,
        pr.status,
        pr.external_post_id AS "externalPostId",
        pr.error_message AS "errorMessage",
        pr.idempotency_key AS "idempotencyKey",
        pr.execution_environment AS "executionEnvironment",
        pr.published_at AS "publishedAt",
        pr.created_at AS "createdAt",
        pr.updated_at AS "updatedAt"
      FROM platform_publish_results pr
      WHERE pr.workspace_id = $1 AND pr.post_id = $2
      ORDER BY pr.created_at ASC;
    `;

    const result = await db.query(queryText, [workspaceId, postId]);
    return result.rows.map(r => ({ ...r, publishedPostId: r.externalPostId }));
  }

  /**
   * Alias for getPublishResultsForPost
   */
  async getResultsForPost(workspaceId, postId) {
    return this.getPublishResultsForPost(workspaceId, postId);
  }

  /**
   * Save or update a platform publish result for a post.
   * Supports both savePublishResult(workspaceId, postId, data) and savePublishResult(workspaceId, data)
   */
  async savePublishResult(workspaceId, postIdOrData, maybeData) {
    let postId;
    let data;

    if (typeof postIdOrData === 'object' && postIdOrData !== null && !maybeData) {
      data = postIdOrData;
      postId = data.postId || data.post_id;
    } else {
      postId = postIdOrData;
      data = maybeData || {};
    }

    if (!workspaceId || !postId) throw new Error('Workspace ID and Post ID are required');

    const platform = (data.platform || '').toUpperCase();
    const status = (data.status || 'PROPOSED').toUpperCase();
    const externalPostId = data.externalPostId || data.external_post_id || data.publishedPostId || data.published_post_id || null;
    const errorMessage = data.errorMessage || data.error_message || null;
    const idempotencyKey = data.idempotencyKey || data.idempotency_key || (data.rawResponse && data.rawResponse.idempotencyKey) || null;
    const executionEnvironment = data.executionEnvironment || 'MOCK';
    const publishedAt = data.publishedAt ? new Date(data.publishedAt).toISOString() : (status === 'SUCCESS' ? new Date().toISOString() : null);

    if (!db.isConfigured()) {
      let key = `${postId}_${platform}`;
      const item = {
        id: data.id || 'pr1a2b3c-4d5e-6f7a-8b9c-' + Date.now().toString(16).padStart(12, '0'),
        postId,
        workspaceId,
        platform,
        status,
        externalPostId,
        publishedPostId: externalPostId,
        errorMessage,
        idempotencyKey,
        executionEnvironment,
        publishedAt,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(key, item);
      return { ...item };
    }

    const queryText = `
      INSERT INTO platform_publish_results (
        workspace_id,
        post_id,
        platform,
        status,
        external_post_id,
        error_message,
        idempotency_key,
        execution_environment,
        published_at,
        created_at,
        updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
      ON CONFLICT (post_id, platform)
      DO UPDATE SET
        status = EXCLUDED.status,
        external_post_id = COALESCE(EXCLUDED.external_post_id, platform_publish_results.external_post_id),
        error_message = EXCLUDED.error_message,
        idempotency_key = COALESCE(EXCLUDED.idempotency_key, platform_publish_results.idempotency_key),
        execution_environment = EXCLUDED.execution_environment,
        published_at = COALESCE(EXCLUDED.published_at, platform_publish_results.published_at),
        updated_at = NOW()
      RETURNING 
        id,
        post_id AS "postId",
        workspace_id AS "workspaceId",
        platform,
        status,
        external_post_id AS "externalPostId",
        error_message AS "errorMessage",
        idempotency_key AS "idempotencyKey",
        execution_environment AS "executionEnvironment",
        published_at AS "publishedAt",
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      postId,
      platform,
      status,
      externalPostId,
      errorMessage,
      idempotencyKey,
      executionEnvironment,
      publishedAt ? new Date(publishedAt) : null
    ]);

    return result.rows[0];
  }
}

module.exports = new PublishResultService();
