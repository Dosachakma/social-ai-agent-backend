const db = require('../db/pool');

// Mock memory memberships for test / offline mock mode
const mockMemberships = new Map([
  [
    'a1b2c3d4-e5f6-4a1b-8c2d-1234567890ab_11111111-2222-4333-8444-555555555555',
    {
      membershipId: 'wm-mock-001',
      role: 'owner',
      workspaceId: '11111111-2222-4333-8444-555555555555',
      userId: 'a1b2c3d4-e5f6-4a1b-8c2d-1234567890ab',
      workspaceName: 'Primary Workspace',
      workspaceSlug: 'primary-workspace',
      organizationId: 'org-mock-001'
    }
  ]
]);

/**
 * Service for tenant-isolated workspace operations.
 */
class WorkspaceService {
  /**
   * List all workspaces accessible to the authenticated user.
   * @param {string} userId - UUID of the authenticated user.
   * @returns {Promise<Array>} List of workspace objects with membership roles and organization details.
   */
  async getUserWorkspaces(userId) {
    if (!userId) {
      throw new Error('User ID is required to fetch workspaces');
    }

    if (!db.isConfigured()) {
      const list = [];
      for (const [key, mem] of mockMemberships.entries()) {
        if (mem.userId === userId) {
          list.push({
            id: mem.workspaceId,
            name: mem.workspaceName,
            slug: mem.workspaceSlug,
            role: mem.role,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            organization: {
              id: mem.organizationId,
              name: 'Default Org',
              slug: 'default-org'
            }
          });
        }
      }
      return list;
    }

    const queryText = `
      SELECT 
        w.id,
        w.name,
        w.slug,
        wm.role,
        w.created_at AS "createdAt",
        w.updated_at AS "updatedAt",
        json_build_object(
          'id', o.id,
          'name', o.name,
          'slug', o.slug
        ) AS organization
      FROM workspaces w
      INNER JOIN workspace_members wm ON w.id = wm.workspace_id
      INNER JOIN organizations o ON w.organization_id = o.id
      WHERE wm.user_id = $1
      ORDER BY w.name ASC;
    `;

    const result = await db.query(queryText, [userId]);
    return result.rows;
  }

  /**
   * Verifies if a user has authenticated membership in a specified workspace.
   * Do NOT trust a client-supplied workspace ID without this verification.
   * @param {string} userId - UUID of the authenticated user.
   * @param {string} workspaceId - UUID of the target workspace.
   * @param {Array<string>} [allowedRoles] - Optional list of required roles (e.g. ['owner', 'admin']).
   * @returns {Promise<Object|null>} Returns workspace membership details or null if unauthorized.
   */
  async verifyWorkspaceMembership(userId, workspaceId, allowedRoles = []) {
    if (!userId || !workspaceId) {
      return null;
    }

    if (!db.isConfigured()) {
      const memKey = `${userId}_${workspaceId}`;
      const membership = mockMemberships.get(memKey);
      if (!membership) {
        return null;
      }
      if (allowedRoles.length > 0 && !allowedRoles.includes(membership.role)) {
        return null;
      }
      return { ...membership };
    }

    const queryText = `
      SELECT 
        wm.id AS "membershipId",
        wm.role,
        wm.workspace_id AS "workspaceId",
        wm.user_id AS "userId",
        w.name AS "workspaceName",
        w.slug AS "workspaceSlug",
        w.organization_id AS "organizationId"
      FROM workspace_members wm
      INNER JOIN workspaces w ON wm.workspace_id = w.id
      WHERE wm.user_id = $1 AND wm.workspace_id = $2;
    `;

    try {
      const result = await db.query(queryText, [userId, workspaceId]);
      if (result.rows.length === 0) {
        return null;
      }

      const membership = result.rows[0];

      // If specific roles are required, verify user has one of them
      if (allowedRoles.length > 0 && !allowedRoles.includes(membership.role)) {
        return null;
      }

      return membership;
    } catch (err) {
      if (err.code === 'ECONNREFUSED' || err.code === 'DB_NOT_CONFIGURED') {
        const memKey = `${userId}_${workspaceId}`;
        const membership = mockMemberships.get(memKey);
        if (!membership) return null;
        if (allowedRoles.length > 0 && !allowedRoles.includes(membership.role)) return null;
        return { ...membership };
      }
      throw err;
    }
  }

  /**
   * Retrieves brand profiles strictly scoped to the verified workspace tenant.
   * @param {string} workspaceId - Verified workspace UUID.
   * @returns {Promise<Array>} List of brand profiles for the tenant.
   */
  async getBrandProfiles(workspaceId) {
    if (!workspaceId) {
      throw new Error('Workspace ID is required');
    }

    const brandProfileService = require('./brandProfileService');
    return brandProfileService.getBrandProfiles(workspaceId);
  }
}

module.exports = new WorkspaceService();
