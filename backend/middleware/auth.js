const jwtService = require('../services/jwtService');
const db = require('../db/pool');

/**
 * Production-Grade SaaS Authentication Middleware
 * 
 * Enforces cryptographic JWT / OIDC token verification for all protected routes.
 * 
 * Security Invariants:
 * 1. Cryptographically verifies JWT signature (HS256 / RS256).
 * 2. Enforces configured algorithm matching (rejects algorithm confusion and alg=none).
 * 3. Enforces issuer (iss), audience (aud), expiration (exp), not-before (nbf), and subject (sub) claims.
 * 4. Resolves authenticated identity strictly from verified token claims.
 * 5. Fails closed in production if auth secrets or keys are missing.
 * 6. Never trusts X-User-Id, body userId, or query userId in production.
 * 7. Never logs raw JWTs, Authorization headers, or crypto secrets.
 * 8. Returns standardized 401 responses for all authentication failures.
 */
async function authenticateUser(req, res, next) {
  const authHeader = req.headers['authorization'];
  const isProduction = process.env.NODE_ENV === 'production';
  const devBypassEnabled = !isProduction && process.env.DEV_AUTH_BYPASS === 'true';

  // 1. Fail closed in production when JWT authentication configuration is missing
  if (isProduction && !jwtService.isConfigured()) {
    console.error('CRITICAL: Authentication configuration missing in production environment. Failing closed.');
    return res.status(401).json({
      success: false,
      error: 'UNAUTHORIZED',
      message: 'Authentication service unconfigured in production environment (fail closed).'
    });
  }

  // 2. Process Authorization header if provided
  if (authHeader && typeof authHeader === 'string') {
    if (!authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'Invalid Authorization header scheme. Bearer token required.'
      });
    }

    const token = authHeader.substring(7).trim();
    if (!token) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'Empty Bearer token provided in Authorization header.'
      });
    }

    // Cryptographic JWT verification
    const verification = jwtService.verifyToken(token);

    if (verification.valid) {
      const claims = verification.claims;
      const verifiedUserId = claims.sub;

      if (!verifiedUserId || typeof verifiedUserId !== 'string' || verifiedUserId.trim() === '') {
        return res.status(401).json({
          success: false,
          error: 'UNAUTHORIZED',
          message: 'Token missing required subject identity claim.'
        });
      }

      // If database is configured, resolve user from users table
      if (db.isConfigured()) {
        try {
          const userResult = await db.query(
            'SELECT id, email, full_name, avatar_url FROM users WHERE id = $1',
            [verifiedUserId]
          );

          if (userResult.rows.length === 0) {
            return res.status(401).json({
              success: false,
              error: 'UNAUTHORIZED',
              message: 'Authenticated user does not exist.'
            });
          }

          const dbUser = userResult.rows[0];
          req.user = {
            id: dbUser.id,
            email: dbUser.email || claims.email || null,
            name: dbUser.full_name || claims.name || claims.full_name || null,
            avatarUrl: dbUser.avatar_url || null,
            claims: claims,
            authenticatedAt: new Date().toISOString()
          };
          return next();
        } catch (dbErr) {
          console.error('Database query error during user identity verification:', dbErr.message);
          return res.status(500).json({
            success: false,
            error: 'INTERNAL_ERROR',
            message: 'Error verifying user identity.'
          });
        }
      }

      // When DB is not configured (Mock / Offline / Unit Test mode)
      req.user = {
        id: verifiedUserId,
        email: claims.email || null,
        name: claims.name || claims.full_name || null,
        claims: claims,
        authenticatedAt: new Date().toISOString()
      };

      return next();
    }

    // If JWT verification failed:
    // In production or when dev bypass is disabled, reject immediately
    if (isProduction || !devBypassEnabled) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: verification.message || 'Authentication token validation failed.'
      });
    }

    // In local dev/test mode ONLY (when DEV_AUTH_BYPASS=true and token is non-JWT plain identifier):
    if (devBypassEnabled && !token.includes('.')) {
      req.user = {
        id: token,
        email: req.headers['x-user-email'] || null,
        authenticatedAt: new Date().toISOString()
      };
      return next();
    }

    return res.status(401).json({
      success: false,
      error: 'UNAUTHORIZED',
      message: verification.message || 'Authentication token validation failed.'
    });
  }

  // 3. Development-only header fallback (strictly disabled in production)
  const userIdHeader = req.headers['x-user-id'];
  if (devBypassEnabled && userIdHeader && typeof userIdHeader === 'string' && userIdHeader.trim() !== '') {
    req.user = {
      id: userIdHeader.trim(),
      email: req.headers['x-user-email'] || null,
      authenticatedAt: new Date().toISOString()
    };
    return next();
  }

  // 4. Missing credentials -> Standardized 401 response
  return res.status(401).json({
    success: false,
    error: 'UNAUTHORIZED',
    message: 'Authentication required. Provide a valid Authorization Bearer token.'
  });
}

module.exports = {
  authenticateUser
};
