const db = require('../db/pool');
const cryptoService = require('./cryptoService');

const memoryStore = new Map();

/**
 * Service for tenant-isolated Social Account management.
 * Sensitive OAuth tokens are encrypted with AES-256-GCM and NEVER exposed through API responses or returned DTOs.
 */
class SocialAccountService {
  /**
   * List all social accounts in the workspace.
   */
  async getAccounts(workspaceId) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    if (!db.isConfigured()) {
      const list = [];
      for (const account of memoryStore.values()) {
        if (account.workspaceId === workspaceId) {
          const sanitized = { ...account };
          delete sanitized.encryptedAccessToken;
          delete sanitized.encryptedRefreshToken;
          delete sanitized.accessToken;
          delete sanitized.refreshToken;
          list.push(sanitized);
        }
      }
      return list;
    }

    const queryText = `
      SELECT 
        id,
        workspace_id AS "workspaceId",
        platform,
        platform_user_id AS "platformUserId",
        account_name AS "accountName",
        handle,
        avatar_url AS "avatarUrl",
        account_type AS "accountType",
        connection_status AS "connectionStatus",
        token_status AS "tokenStatus",
        token_expires_at AS "tokenExpiresAt",
        scopes,
        capabilities AS "availableCapabilities",
        follower_count AS "followerCount",
        posts_today_count AS "postsTodayCount",
        (connection_status = 'CONNECTED' AND token_status = 'VALID') AS "isConnected",
        last_synced_at AS "lastSyncedAt",
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM social_accounts
      WHERE workspace_id = $1
      ORDER BY created_at ASC;
    `;

    const result = await db.query(queryText, [workspaceId]);
    return result.rows;
  }

  /**
   * Get single social account by ID strictly within the tenant workspace.
   */
  async getAccountById(workspaceId, id) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const account = memoryStore.get(id);
      if (account && account.workspaceId === workspaceId) {
        const sanitized = { ...account };
        delete sanitized.encryptedAccessToken;
        delete sanitized.encryptedRefreshToken;
        delete sanitized.accessToken;
        delete sanitized.refreshToken;
        return sanitized;
      }
      return null;
    }

    const queryText = `
      SELECT 
        id,
        workspace_id AS "workspaceId",
        platform,
        platform_user_id AS "platformUserId",
        account_name AS "accountName",
        handle,
        avatar_url AS "avatarUrl",
        account_type AS "accountType",
        connection_status AS "connectionStatus",
        token_status AS "tokenStatus",
        token_expires_at AS "tokenExpiresAt",
        scopes,
        capabilities AS "availableCapabilities",
        follower_count AS "followerCount",
        posts_today_count AS "postsTodayCount",
        (connection_status = 'CONNECTED' AND token_status = 'VALID') AS "isConnected",
        last_synced_at AS "lastSyncedAt",
        created_at AS "createdAt",
        updated_at AS "updatedAt"
      FROM social_accounts
      WHERE workspace_id = $1 AND id = $2;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rows[0] || null;
  }

  /**
   * Connect or update a social account in the workspace.
   */
  async connectAccount(workspaceId, data) {
    if (!workspaceId) throw new Error('Workspace ID is required');

    const platform = (data.platform || '').toUpperCase();
    const platformUserId = data.platformUserId || data.platform_user_id || data.id || 'unknown_id';
    const accountName = data.accountName || data.name || `${platform} Account`;
    const handle = data.handle || (data.name ? `@${data.name.toLowerCase().replace(/\s+/g, '')}` : `@${platform.toLowerCase()}`);
    const avatarUrl = data.avatarUrl || data.profileImageUrl || '';
    const accountType = data.accountType || 'PAGE';
    const connectionStatus = data.connectionStatus || 'CONNECTED';
    const tokenStatus = data.tokenStatus || 'VALID';
    const rawAccessToken = data.accessToken || data.encryptedAccessToken || null;
    const rawRefreshToken = data.refreshToken || data.encryptedRefreshToken || null;
    const encryptedAccessToken = rawAccessToken ? cryptoService.encrypt(rawAccessToken) : null;
    const encryptedRefreshToken = rawRefreshToken ? cryptoService.encrypt(rawRefreshToken) : null;
    const tokenExpiresAt = data.tokenExpiresAt || null;
    const scopes = data.scopes || ['public_profile', 'pages_manage_posts', 'instagram_basic'];
    const capabilities = data.availableCapabilities || data.capabilities || [
      'CREATE_POST', 'PUBLISH_POST', 'READ_COMMENTS', 'REPLY_COMMENT', 'READ_ANALYTICS', 'MEDIA_UPLOAD'
    ];
    const followerCount = parseInt(data.followerCount || 0, 10);
    const postsTodayCount = parseInt(data.postsTodayCount || 0, 10);

    if (!db.isConfigured()) {
      // Find existing by platform & platformUserId
      let existingId = null;
      for (const [key, acc] of memoryStore.entries()) {
        if (acc.workspaceId === workspaceId && acc.platform === platform && acc.platformUserId === platformUserId) {
          existingId = key;
          break;
        }
      }

      const id = existingId || data.id || 'a1b2c3d4-e5f6-4a1b-8c2d-' + Date.now().toString(16).padStart(12, '0');
      const item = {
        id,
        workspaceId,
        platform,
        platformUserId,
        accountName,
        handle,
        avatarUrl,
        accountType,
        connectionStatus,
        tokenStatus,
        tokenExpiresAt,
        scopes,
        availableCapabilities: capabilities,
        followerCount,
        postsTodayCount,
        isConnected: connectionStatus === 'CONNECTED' && tokenStatus === 'VALID',
        lastSyncedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      // Persist internal encrypted credentials in vault store
      memoryStore.set(id, {
        ...item,
        encryptedAccessToken,
        encryptedRefreshToken
      });
      // Return sanitized account object with zero credential leakage
      return { ...item };
    }

    const queryText = `
      INSERT INTO social_accounts (
        workspace_id,
        platform,
        platform_user_id,
        account_name,
        handle,
        avatar_url,
        account_type,
        connection_status,
        token_status,
        encrypted_access_token,
        encrypted_refresh_token,
        token_expires_at,
        scopes,
        capabilities,
        follower_count,
        posts_today_count,
        last_synced_at,
        created_at,
        updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13::jsonb, $14::jsonb, $15, $16, NOW(), NOW(), NOW())
      ON CONFLICT (workspace_id, platform, platform_user_id) 
      DO UPDATE SET
        account_name = EXCLUDED.account_name,
        handle = EXCLUDED.handle,
        avatar_url = EXCLUDED.avatar_url,
        account_type = EXCLUDED.account_type,
        connection_status = EXCLUDED.connection_status,
        token_status = EXCLUDED.token_status,
        encrypted_access_token = COALESCE(EXCLUDED.encrypted_access_token, social_accounts.encrypted_access_token),
        encrypted_refresh_token = COALESCE(EXCLUDED.encrypted_refresh_token, social_accounts.encrypted_refresh_token),
        token_expires_at = COALESCE(EXCLUDED.token_expires_at, social_accounts.token_expires_at),
        scopes = EXCLUDED.scopes,
        capabilities = EXCLUDED.capabilities,
        follower_count = EXCLUDED.follower_count,
        last_synced_at = NOW(),
        updated_at = NOW()
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        platform,
        platform_user_id AS "platformUserId",
        account_name AS "accountName",
        handle,
        avatar_url AS "avatarUrl",
        account_type AS "accountType",
        connection_status AS "connectionStatus",
        token_status AS "tokenStatus",
        token_expires_at AS "tokenExpiresAt",
        scopes,
        capabilities AS "availableCapabilities",
        follower_count AS "followerCount",
        posts_today_count AS "postsTodayCount",
        (connection_status = 'CONNECTED' AND token_status = 'VALID') AS "isConnected",
        last_synced_at AS "lastSyncedAt",
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      platform,
      platformUserId,
      accountName,
      handle,
      avatarUrl,
      accountType,
      connectionStatus,
      tokenStatus,
      encryptedAccessToken,
      encryptedRefreshToken,
      tokenExpiresAt,
      JSON.stringify(scopes),
      JSON.stringify(capabilities),
      followerCount,
      postsTodayCount
    ]);

    return result.rows[0];
  }

  /**
   * Update an existing social account.
   */
  async updateAccount(workspaceId, id, data) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const existing = memoryStore.get(id);
      if (!existing || existing.workspaceId !== workspaceId) return null;

      const connectionStatus = data.connectionStatus !== undefined ? data.connectionStatus : existing.connectionStatus;
      const tokenStatus = data.tokenStatus !== undefined ? data.tokenStatus : existing.tokenStatus;
      const rawAccessToken = data.accessToken || data.encryptedAccessToken;
      const rawRefreshToken = data.refreshToken || data.encryptedRefreshToken;
      const encryptedAccessToken = rawAccessToken !== undefined
        ? (rawAccessToken ? cryptoService.encrypt(rawAccessToken) : null)
        : existing.encryptedAccessToken;
      const encryptedRefreshToken = rawRefreshToken !== undefined
        ? (rawRefreshToken ? cryptoService.encrypt(rawRefreshToken) : null)
        : existing.encryptedRefreshToken;

      const updated = {
        ...existing,
        connectionStatus,
        tokenStatus,
        encryptedAccessToken,
        encryptedRefreshToken,
        accountName: data.accountName !== undefined ? data.accountName : existing.accountName,
        handle: data.handle !== undefined ? data.handle : existing.handle,
        avatarUrl: data.avatarUrl !== undefined ? data.avatarUrl : existing.avatarUrl,
        followerCount: data.followerCount !== undefined ? parseInt(data.followerCount, 10) : existing.followerCount,
        postsTodayCount: data.postsTodayCount !== undefined ? parseInt(data.postsTodayCount, 10) : existing.postsTodayCount,
        isConnected: connectionStatus === 'CONNECTED' && tokenStatus === 'VALID',
        lastSyncedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      memoryStore.set(id, updated);
      const sanitized = { ...updated };
      delete sanitized.encryptedAccessToken;
      delete sanitized.encryptedRefreshToken;
      delete sanitized.accessToken;
      delete sanitized.refreshToken;
      return sanitized;
    }

    const existing = await this.getAccountById(workspaceId, id);
    if (!existing) return null;

    const connectionStatus = data.connectionStatus !== undefined ? data.connectionStatus : existing.connectionStatus;
    const tokenStatus = data.tokenStatus !== undefined ? data.tokenStatus : existing.tokenStatus;
    const accountName = data.accountName !== undefined ? data.accountName : existing.accountName;
    const handle = data.handle !== undefined ? data.handle : existing.handle;
    const avatarUrl = data.avatarUrl !== undefined ? data.avatarUrl : existing.avatarUrl;
    const followerCount = data.followerCount !== undefined ? parseInt(data.followerCount, 10) : existing.followerCount;
    const postsTodayCount = data.postsTodayCount !== undefined ? parseInt(data.postsTodayCount, 10) : existing.postsTodayCount;
    const rawAccessToken = data.accessToken || data.encryptedAccessToken;
    const rawRefreshToken = data.refreshToken || data.encryptedRefreshToken;
    const encryptedAccessToken = rawAccessToken !== undefined
      ? (rawAccessToken ? cryptoService.encrypt(rawAccessToken) : null)
      : null;
    const encryptedRefreshToken = rawRefreshToken !== undefined
      ? (rawRefreshToken ? cryptoService.encrypt(rawRefreshToken) : null)
      : null;

    const queryText = `
      UPDATE social_accounts SET
        connection_status = $3,
        token_status = $4,
        account_name = $5,
        handle = $6,
        avatar_url = $7,
        follower_count = $8,
        posts_today_count = $9,
        encrypted_access_token = COALESCE($10, social_accounts.encrypted_access_token),
        encrypted_refresh_token = COALESCE($11, social_accounts.encrypted_refresh_token),
        last_synced_at = NOW(),
        updated_at = NOW()
      WHERE workspace_id = $1 AND id = $2
      RETURNING 
        id,
        workspace_id AS "workspaceId",
        platform,
        platform_user_id AS "platformUserId",
        account_name AS "accountName",
        handle,
        avatar_url AS "avatarUrl",
        account_type AS "accountType",
        connection_status AS "connectionStatus",
        token_status AS "tokenStatus",
        token_expires_at AS "tokenExpiresAt",
        scopes,
        capabilities AS "availableCapabilities",
        follower_count AS "followerCount",
        posts_today_count AS "postsTodayCount",
        (connection_status = 'CONNECTED' AND token_status = 'VALID') AS "isConnected",
        last_synced_at AS "lastSyncedAt",
        created_at AS "createdAt",
        updated_at AS "updatedAt";
    `;

    const result = await db.query(queryText, [
      workspaceId,
      id,
      connectionStatus,
      tokenStatus,
      accountName,
      handle,
      avatarUrl,
      followerCount,
      postsTodayCount,
      encryptedAccessToken,
      encryptedRefreshToken
    ]);

    return result.rows[0] || null;
  }

  /**
   * Disconnect or delete social account from workspace.
   */
  async deleteAccount(workspaceId, id) {
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
      DELETE FROM social_accounts
      WHERE workspace_id = $1 AND id = $2
      RETURNING id;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    return result.rowCount > 0;
  }

  /**
   * Internal secure server-side helper: Decrypts and retrieves tokens strictly within tenant boundaries.
   * NEVER exposed to HTTP handlers or API responses.
   */
  async getDecryptedAccountTokens(workspaceId, id) {
    if (!workspaceId || !id) return null;

    if (!db.isConfigured()) {
      const account = memoryStore.get(id);
      if (account && account.workspaceId === workspaceId) {
        return {
          accessToken: account.encryptedAccessToken ? cryptoService.decrypt(account.encryptedAccessToken) : null,
          refreshToken: account.encryptedRefreshToken ? cryptoService.decrypt(account.encryptedRefreshToken) : null
        };
      }
      return null;
    }

    const queryText = `
      SELECT encrypted_access_token, encrypted_refresh_token
      FROM social_accounts
      WHERE workspace_id = $1 AND id = $2;
    `;

    const result = await db.query(queryText, [workspaceId, id]);
    if (!result.rows[0]) return null;

    const row = result.rows[0];
    return {
      accessToken: row.encrypted_access_token ? cryptoService.decrypt(row.encrypted_access_token) : null,
      refreshToken: row.encrypted_refresh_token ? cryptoService.decrypt(row.encrypted_refresh_token) : null
    };
  }
}

module.exports = new SocialAccountService();
