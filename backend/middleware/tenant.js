const workspaceService = require('../services/workspaceService');

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * Tenant isolation middleware.
 * Verifies that the authenticated user is an active member of the requested workspace
 * before allowing any route or database query to proceed.
 * 
 * Never trusts a client-supplied workspace ID without validating membership.
 * 
 * @param {Object} [options]
 * @param {Array<string>} [options.roles] - Optional list of required roles (e.g., ['owner', 'admin'])
 */
function enforceWorkspaceAccess(options = {}) {
  const allowedRoles = options.roles || [];

  return async (req, res, next) => {
    // 1. Verify user authentication context exists
    if (!req.user || !req.user.id) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'Authentication required prior to workspace authorization.'
      });
    }

    // 2. Extract workspace ID from params, query, headers, or body
    const workspaceId = req.params.workspaceId || 
                        req.query.workspaceId || 
                        req.headers['x-workspace-id'] || 
                        req.body?.workspaceId;

    if (!workspaceId || typeof workspaceId !== 'string' || workspaceId.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'MISSING_WORKSPACE_ID',
        message: 'A valid workspace ID is required for this operation.'
      });
    }

    const cleanWorkspaceId = workspaceId.trim();

    // 3. Validate UUID format
    if (!UUID_REGEX.test(cleanWorkspaceId)) {
      return res.status(400).json({
        success: false,
        error: 'INVALID_WORKSPACE_ID',
        message: 'Workspace ID must be a valid UUID.'
      });
    }

    try {
      // 4. Verify membership in database (Strict Tenant Isolation)
      const membership = await workspaceService.verifyWorkspaceMembership(
        req.user.id,
        cleanWorkspaceId,
        allowedRoles
      );

      if (!membership) {
        return res.status(403).json({
          success: false,
          error: 'FORBIDDEN_WORKSPACE_ACCESS',
          message: 'Access denied to the specified workspace or workspace does not exist.'
        });
      }

      // 5. Attach verified workspace and membership details to request context
      req.workspaceId = cleanWorkspaceId;
      req.workspace = {
        id: cleanWorkspaceId,
        name: membership.workspaceName,
        slug: membership.workspaceSlug,
        organizationId: membership.organizationId
      };
      req.membership = {
        id: membership.membershipId,
        role: membership.role
      };

      next();
    } catch (err) {
      console.error('Error verifying workspace membership:', err.message);
      return res.status(500).json({
        success: false,
        error: 'TENANT_VERIFICATION_ERROR',
        message: 'Internal server error while verifying workspace authorization.'
      });
    }
  };
}

module.exports = {
  enforceWorkspaceAccess,
  UUID_REGEX
};
