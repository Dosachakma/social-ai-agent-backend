const db = require('../db/pool');

const memoryStore = new Map();

/**
 * Service for tenant-isolated AI Agent Action Logs.
 */
class AgentLogService {
  /**
   * List agent action logs in the workspace with filtering & pagination.
   */
  async getLogs(workspaceId, options = {}) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    if (!db.isConfigured()) {
      let list = [];
      for (const log of memoryStore.values()) {
        if (log.workspaceId === workspaceId) {
          list.push({ ...log });
        }
      }

      if (options.action) {
        const action = options.action.toUpperCase();
        list = list.filter(l => l.action === action);
      }

      if (options.status) {
        const status = options.status.toUpperCase();
        list = list.filter(l => l.status === status);
      }

      if (options.platform) {
        const plat = options.platform.toUpperCase();
        list = list.filter(l => l.platform === plat);
      }

      list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      if (options.offset) {
        const offset = parseInt(options.offset, 10);
        if (!isNaN(offset)) list = list.slice(offset);
      }

      if (options.limit) {
        const limit = parseInt(options.limit, 10);
        if (!isNaN(limit)) list = list.slice(0, limit);
      }

      return list;
    }

    let queryText = `
      SELECT 
        l.id,
        l.workspace_id AS "workspaceId",
        l.user_id AS "userId",
        l.action,
        l.platform,
        l.status,
        l.execution_environment AS "executionEnvironment",
        l.error,
        l.metadata,
        l.created_at AS "createdAt",
        l.created_at AS "timestamp"
      FROM agent_action_logs l
      WHERE l.workspace_id = $1
    `;

    const params = [workspaceId];
    let paramIndex = 2;

    if (options.action) {
      queryText += ` AND l.action = $${paramIndex++}`;
      params.push(options.action.toUpperCase());
    }

    if (options.status) {
      queryText += ` AND l.status = $${paramIndex++}`;
      params.push(options.status.toUpperCase());
    }

    if (options.platform) {
      queryText += ` AND l.platform = $${paramIndex++}`;
      params.push(options.platform.toUpperCase());
    }

    queryText += ` ORDER BY l.created_at DESC`;

    const limit = parseInt(options.limit || '50', 10);
    if (!isNaN(limit) && limit > 0) {
      queryText += ` LIMIT $${paramIndex++}`;
      params.push(limit);
    }

    const offset = parseInt(options.offset || '0', 10);
    if (!isNaN(offset) && offset >= 0) {
      queryText += ` OFFSET $${paramIndex++}`;
      params.push(offset);
    }

    const result = await db.query(queryText, params);
    return result.rows;
  }

  /**
   * Create an agent action log entry in the workspace.
   */
  async createLog(workspaceId, data, userId = null) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    const action = data.action;
    const platform = data.platform || null;
    const status = (data.status || 'PROPOSED').toUpperCase();
    const executionEnvironment = data.executionEnvironment || 'MOCK';
    const error = data.error || null;
    const metadata = data.metadata || {};

    if (!db.isConfigured()) {
      const id = data.id || 'log1a2b3-4c5d-6e7f-8a9b-' + Date.now().toString(16).padStart(12, '0');
      const item = {
        id,
        workspaceId,
        userId: userId || null,
        action,
        platform,
        status,
        executionEnvironment,
        error,
        metadata,
        createdAt: new Date().toISOString(),
        timestamp: new Date().toISOString()
      };
      memoryStore.set(id, item);
      return { ...item };
    }

    const queryText = `
      INSERT INTO agent_action_logs (
        workspace_id,
        user_id,
        action,
        platform,
        status,
        execution_environment,
        error,
        metadata,
        created_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, NOW())
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        user_id AS "userId",
        action,
        platform,
        status,
        execution_environment AS "executionEnvironment",
        error,
        metadata,
        created_at AS "createdAt",
        created_at AS "timestamp";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      userId,
      action,
      platform,
      status,
      executionEnvironment,
      error,
      JSON.stringify(metadata)
    ]);

    return result.rows[0];
  }
}

module.exports = new AgentLogService();
