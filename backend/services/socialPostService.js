const crypto = require('crypto');
const db = require('../db/pool');

const memoryStore = new Map();

/**
 * Service for tenant-isolated Social Posts, Drafts, and Scheduled Posts.
 */
class SocialPostService {
  /**
   * List posts in the workspace with flexible filtering.
   */
  async getPosts(workspaceId, filters = {}) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    if (!db.isConfigured()) {
      let list = [];
      for (const post of memoryStore.values()) {
        if (post.workspaceId === workspaceId) {
          list.push({ ...post });
        }
      }

      if (filters.status) {
        const targetStatus = filters.status.toUpperCase();
        list = list.filter(p => p.status === targetStatus);
      }

      if (filters.approvalState) {
        const targetState = filters.approvalState.toUpperCase();
        list = list.filter(p => p.approvalState === targetState);
      }

      if (filters.platform) {
        const targetPlatform = filters.platform.toUpperCase();
        list = list.filter(p => Array.isArray(p.targetPlatforms) && p.targetPlatforms.includes(targetPlatform));
      }

      if (filters.search) {
        const q = filters.search.toLowerCase();
        list = list.filter(p => 
          (p.title && p.title.toLowerCase().includes(q)) || 
          (p.content && p.content.toLowerCase().includes(q)) ||
          (p.hashtags && p.hashtags.toLowerCase().includes(q))
        );
      }

      list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      if (filters.offset) {
        const offset = parseInt(filters.offset, 10);
        if (!isNaN(offset)) list = list.slice(offset);
      }

      if (filters.limit) {
        const limit = parseInt(filters.limit, 10);
        if (!isNaN(limit)) list = list.slice(0, limit);
      }

      return list;
    }

    let queryText = `
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
        p.published_at AS "publishedAt",
        p.media_urls AS "mediaUrls",
        p.hashtags,
        p.cta,
        p.is_ai_generated AS "isAiGenerated",
        p.error_message AS "errorMessage",
        p.retry_count AS "retryCount",
        p.max_retries AS "maxRetries",
        p.engagement_score AS "engagementScore",
        p.engagement_count AS "engagementCount",
        p.created_at AS "createdAt",
        p.updated_at AS "updatedAt",
        COALESCE(
          (
            SELECT json_agg(
              json_build_object(
                'id', pr.id,
                'platform', pr.platform,
                'status', pr.status,
                'externalPostId', pr.external_post_id,
                'errorMessage', pr.error_message,
                'idempotencyKey', pr.idempotency_key,
                'executionEnvironment', pr.execution_environment,
                'publishedAt', pr.published_at
              )
            )
            FROM platform_publish_results pr
            WHERE pr.post_id = p.id
          ), '[]'::json
        ) AS "platformPublishResults"
      FROM social_posts p
      WHERE p.workspace_id = $1
    `;

    const params = [workspaceId];
    let paramIndex = 2;

    if (filters.status) {
      queryText += ` AND p.status = $${paramIndex++}`;
      params.push(filters.status.toUpperCase());
    }

    if (filters.approvalState) {
      queryText += ` AND p.approval_state = $${paramIndex++}`;
      params.push(filters.approvalState.toUpperCase());
    }

    if (filters.isAiGenerated !== undefined) {
      queryText += ` AND p.is_ai_generated = $${paramIndex++}`;
      params.push(filters.isAiGenerated === 'true' || filters.isAiGenerated === true);
    }

    if (filters.platform) {
      queryText += ` AND p.target_platforms ? $${paramIndex++}`;
      params.push(filters.platform.toUpperCase());
    }

    if (filters.search) {
      queryText += ` AND (p.title ILIKE $${paramIndex} OR p.content ILIKE $${paramIndex} OR p.hashtags ILIKE $${paramIndex})`;
      params.push(`%${filters.search}%`);
      paramIndex++;
    }

    queryText += ` ORDER BY p.created_at DESC`;

    if (filters.limit) {
      const limit = parseInt(filters.limit, 10);
      if (!isNaN(limit) && limit > 0) {
        queryText += ` LIMIT $${paramIndex++}`;
        params.push(limit);
      }
    }

    if (filters.offset) {
      const offset = parseInt(filters.offset, 10);
      if (!isNaN(offset) && offset >= 0) {
        queryText += ` OFFSET $${paramIndex++}`;
        params.push(offset);
      }
    }

    const result = await db.query(queryText, params);
    return result.rows;
  }

  /**
   * Get draft posts for workspace.
   */
  async getDrafts(workspaceId) {
    return this.getPosts(workspaceId, { status: 'DRAFT' });
  }

  /**
   * Get scheduled posts for workspace.
   */
  async getScheduledPosts(workspaceId) {
    return this.getPosts(workspaceId, { status: 'SCHEDULED' });
  }

  /**
   * Get single post by ID with all publish results.
   */
  async getPostById(workspaceId, id) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const post = memoryStore.get(id);
      if (post && post.workspaceId === workspaceId) {
        return { ...post };
      }
      return null;
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
        p.published_at AS "publishedAt",
        p.media_urls AS "mediaUrls",
        p.hashtags,
        p.cta,
        p.is_ai_generated AS "isAiGenerated",
        p.error_message AS "errorMessage",
        p.retry_count AS "retryCount",
        p.max_retries AS "maxRetries",
        p.engagement_score AS "engagementScore",
        p.engagement_count AS "engagementCount",
        p.created_at AS "createdAt",
        p.updated_at AS "updatedAt",
        COALESCE(
          (
            SELECT json_agg(
              json_build_object(
                'id', pr.id,
                'platform', pr.platform,
                'status', pr.status,
                'externalPostId', pr.external_post_id,
                'errorMessage', pr.error_message,
                'idempotencyKey', pr.idempotency_key,
                'executionEnvironment', pr.execution_environment,
                'publishedAt', pr.published_at
              )
            )
            FROM platform_publish_results pr
            WHERE pr.post_id = p.id
          ), '[]'::json
        ) AS "platformPublishResults"
      FROM social_posts p
      WHERE p.workspace_id = $1 AND p.id = $2;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rows[0] || null;
  }

  /**
   * Create a new post / draft / scheduled post in the workspace.
   */
  async createPost(workspaceId, data, userId = null) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    const title = data.title;
    const content = data.content || data.caption;
    const targetPlatforms = data.targetPlatforms || data.platforms || [];
    const status = (data.status || (data.scheduledAt || data.scheduledTime ? 'SCHEDULED' : 'DRAFT')).toUpperCase();
    const approvalState = (data.approvalState || 'PROPOSED').toUpperCase();
    const scheduledAt = data.scheduledAt ? new Date(data.scheduledAt).toISOString() : null;
    const scheduledTime = data.scheduledTime || (data.scheduledAt ? new Date(data.scheduledAt).toLocaleString() : null);
    const timezone = data.timezone || 'UTC';
    const repeatOption = data.repeatOption || 'NONE';
    const requireApproval = data.requireApproval !== undefined ? data.requireApproval : true;
    const mediaUrls = data.mediaUrls || (data.mediaUrl ? [data.mediaUrl] : (data.media || []));
    const hashtags = data.hashtags || '';
    const cta = data.cta || '';
    const isAiGenerated = data.isAiGenerated !== undefined ? data.isAiGenerated : (data.aiGenerated || false);
    const engagementScore = data.engagementScore !== undefined ? parseInt(data.engagementScore, 10) : 88;

    if (!db.isConfigured()) {
      const id = data.id || crypto.randomUUID();
      const item = {
        id,
        workspaceId,
        createdByUserId: userId,
        title,
        content,
        caption: content,
        targetPlatforms,
        status,
        approvalState,
        scheduledAt,
        scheduledTime,
        timezone,
        repeatOption,
        requireApproval,
        publishedAt: status === 'PUBLISHED' ? new Date().toISOString() : null,
        mediaUrls,
        hashtags,
        cta,
        isAiGenerated,
        errorMessage: null,
        retryCount: 0,
        maxRetries: 3,
        engagementScore,
        engagementCount: 0,
        platformPublishResults: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(id, item);
      return { ...item };
    }

    const queryText = `
      INSERT INTO social_posts (
        workspace_id,
        created_by_user_id,
        title,
        content,
        target_platforms,
        status,
        approval_state,
        scheduled_at,
        scheduled_time,
        timezone,
        repeat_option,
        require_approval,
        media_urls,
        hashtags,
        cta,
        is_ai_generated,
        engagement_score,
        created_at,
        updated_at
      ) VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7, $8, $9, $10, $11, $12, $13::jsonb, $14, $15, $16, $17, NOW(), NOW())
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        created_by_user_id AS "createdByUserId",
        title,
        content,
        content AS "caption",
        target_platforms AS "targetPlatforms",
        status,
        approval_state AS "approvalState",
        scheduled_at AS "scheduledAt",
        scheduled_time AS "scheduledTime",
        timezone,
        repeat_option AS "repeatOption",
        require_approval AS "requireApproval",
        published_at AS "publishedAt",
        media_urls AS "mediaUrls",
        hashtags,
        cta,
        is_ai_generated AS "isAiGenerated",
        error_message AS "errorMessage",
        retry_count AS "retryCount",
        max_retries AS "maxRetries",
        engagement_score AS "engagementScore",
        engagement_count AS "engagementCount",
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      userId,
      title,
      content,
      JSON.stringify(targetPlatforms),
      status,
      approvalState,
      scheduledAt ? new Date(scheduledAt) : null,
      scheduledTime,
      timezone,
      repeatOption,
      requireApproval,
      JSON.stringify(mediaUrls),
      hashtags,
      cta,
      isAiGenerated,
      engagementScore
    ]);

    const post = result.rows[0];
    post.platformPublishResults = [];
    return post;
  }

  /**
   * Update an existing post in the workspace.
   */
  async updatePost(workspaceId, id, data) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const existing = memoryStore.get(id);
      if (!existing || existing.workspaceId !== workspaceId) return null;

      const status = (data.status !== undefined ? data.status : existing.status).toUpperCase();
      const updated = {
        ...existing,
        title: data.title !== undefined ? data.title : existing.title,
        content: data.content !== undefined ? data.content : (data.caption !== undefined ? data.caption : existing.content),
        caption: data.content !== undefined ? data.content : (data.caption !== undefined ? data.caption : existing.caption),
        targetPlatforms: data.targetPlatforms !== undefined ? data.targetPlatforms : (data.platforms !== undefined ? data.platforms : existing.targetPlatforms),
        status,
        approvalState: (data.approvalState !== undefined ? data.approvalState : existing.approvalState).toUpperCase(),
        scheduledAt: data.scheduledAt !== undefined ? data.scheduledAt : existing.scheduledAt,
        scheduledTime: data.scheduledTime !== undefined ? data.scheduledTime : existing.scheduledTime,
        timezone: data.timezone !== undefined ? data.timezone : existing.timezone,
        repeatOption: data.repeatOption !== undefined ? data.repeatOption : existing.repeatOption,
        requireApproval: data.requireApproval !== undefined ? data.requireApproval : existing.requireApproval,
        publishedAt: status === 'PUBLISHED' ? new Date().toISOString() : (data.publishedAt !== undefined ? data.publishedAt : existing.publishedAt),
        mediaUrls: data.mediaUrls !== undefined ? data.mediaUrls : (data.media !== undefined ? data.media : existing.mediaUrls),
        hashtags: data.hashtags !== undefined ? data.hashtags : existing.hashtags,
        cta: data.cta !== undefined ? data.cta : existing.cta,
        isAiGenerated: data.isAiGenerated !== undefined ? data.isAiGenerated : (data.aiGenerated !== undefined ? data.aiGenerated : existing.isAiGenerated),
        errorMessage: data.errorMessage !== undefined ? data.errorMessage : existing.errorMessage,
        retryCount: data.retryCount !== undefined ? parseInt(data.retryCount, 10) : existing.retryCount,
        engagementScore: data.engagementScore !== undefined ? parseInt(data.engagementScore, 10) : existing.engagementScore,
        engagementCount: data.engagementCount !== undefined ? parseInt(data.engagementCount, 10) : existing.engagementCount,
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(id, updated);
      return { ...updated };
    }

    const existing = await this.getPostById(workspaceId, id);
    if (!existing) return null;

    const title = data.title !== undefined ? data.title : existing.title;
    const content = data.content !== undefined ? data.content : (data.caption !== undefined ? data.caption : existing.content);
    const targetPlatforms = JSON.stringify(data.targetPlatforms !== undefined ? data.targetPlatforms : (data.platforms !== undefined ? data.platforms : existing.targetPlatforms));
    const status = (data.status !== undefined ? data.status : existing.status).toUpperCase();
    const approvalState = (data.approvalState !== undefined ? data.approvalState : existing.approvalState).toUpperCase();
    const scheduledAt = data.scheduledAt !== undefined ? (data.scheduledAt ? new Date(data.scheduledAt) : null) : existing.scheduledAt;
    const scheduledTime = data.scheduledTime !== undefined ? data.scheduledTime : existing.scheduledTime;
    const timezone = data.timezone !== undefined ? data.timezone : existing.timezone;
    const repeatOption = data.repeatOption !== undefined ? data.repeatOption : existing.repeatOption;
    const requireApproval = data.requireApproval !== undefined ? data.requireApproval : existing.requireApproval;
    const publishedAt = data.publishedAt !== undefined ? (data.publishedAt ? new Date(data.publishedAt) : null) : existing.publishedAt;
    const mediaUrls = JSON.stringify(data.mediaUrls !== undefined ? data.mediaUrls : (data.media !== undefined ? data.media : existing.mediaUrls));
    const hashtags = data.hashtags !== undefined ? data.hashtags : existing.hashtags;
    const cta = data.cta !== undefined ? data.cta : existing.cta;
    const isAiGenerated = data.isAiGenerated !== undefined ? data.isAiGenerated : (data.aiGenerated !== undefined ? data.aiGenerated : existing.isAiGenerated);
    const errorMessage = data.errorMessage !== undefined ? data.errorMessage : existing.errorMessage;
    const retryCount = data.retryCount !== undefined ? parseInt(data.retryCount, 10) : existing.retryCount;
    const engagementScore = data.engagementScore !== undefined ? parseInt(data.engagementScore, 10) : existing.engagementScore;
    const engagementCount = data.engagementCount !== undefined ? parseInt(data.engagementCount, 10) : existing.engagementCount;

    const queryText = `
      UPDATE social_posts SET
        title = $3,
        content = $4,
        target_platforms = $5::jsonb,
        status = $6,
        approval_state = $7,
        scheduled_at = $8,
        scheduled_time = $9,
        timezone = $10,
        repeat_option = $11,
        require_approval = $12,
        published_at = $13,
        media_urls = $14::jsonb,
        hashtags = $15,
        cta = $16,
        is_ai_generated = $17,
        error_message = $18,
        retry_count = $19,
        engagement_score = $20,
        engagement_count = $21,
        updated_at = NOW()
      WHERE workspace_id = $1 AND id = $2
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        created_by_user_id AS "createdByUserId",
        title,
        content,
        content AS "caption",
        target_platforms AS "targetPlatforms",
        status,
        approval_state AS "approvalState",
        scheduled_at AS "scheduledAt",
        scheduled_time AS "scheduledTime",
        timezone,
        repeat_option AS "repeatOption",
        require_approval AS "requireApproval",
        published_at AS "publishedAt",
        media_urls AS "mediaUrls",
        hashtags,
        cta,
        is_ai_generated AS "isAiGenerated",
        error_message AS "errorMessage",
        retry_count AS "retryCount",
        max_retries AS "maxRetries",
        engagement_score AS "engagementScore",
        engagement_count AS "engagementCount",
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      id,
      title,
      content,
      targetPlatforms,
      status,
      approvalState,
      scheduledAt,
      scheduledTime,
      timezone,
      repeatOption,
      requireApproval,
      publishedAt,
      mediaUrls,
      hashtags,
      cta,
      isAiGenerated,
      errorMessage,
      retryCount,
      engagementScore,
      engagementCount
    ]);

    if (result.rows.length === 0) return null;

    const post = result.rows[0];
    post.platformPublishResults = existing.platformPublishResults || [];
    return post;
  }

  /**
   * Delete a post from the workspace (cascades to publish results).
   */
  async deletePost(workspaceId, id) {
    if (!workspaceId || !id) return false;

    if (!db.isConfigured()) {
      const existing = memoryStore.get(id);
      if (existing && existing.workspaceId === workspaceId) {
        memoryStore.delete(id);
        return true;
      }
      return false;
    }

    const queryText = `
      DELETE FROM social_posts
      WHERE workspace_id = $1 AND id = $2
      RETURNING id;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rowCount > 0;
  }

  /**
   * Find approved scheduled posts due for execution across all workspaces (for background scheduler engine).
   */
  async findDuePosts(limit = 50) {
    if (!db.isConfigured()) {
      const now = Date.now();
      const due = [];
      for (const post of memoryStore.values()) {
        const schedTime = post.scheduledAt ? new Date(post.scheduledAt).getTime() : 0;
        const isSched = post.status === 'SCHEDULED';
        const isAppr = post.approvalState === 'APPROVED';
        const isDue = schedTime > 0 && schedTime <= now;
        if (isSched && isAppr && isDue) {
          due.push({ ...post });
          if (due.length >= limit) break;
        }
      }
      return due;
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
}

module.exports = new SocialPostService();
