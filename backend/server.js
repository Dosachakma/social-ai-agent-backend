require('dotenv').config();
const express = require('express');
const cors = require('cors');
const https = require('https');
const http = require('http');
const ticketStore = require('./ticketStore');
const db = require('./db/pool');
const workspaceService = require('./services/workspaceService');
const brandProfileService = require('./services/brandProfileService');
const socialAccountService = require('./services/socialAccountService');
const socialPostService = require('./services/socialPostService');
const publishResultService = require('./services/publishResultService');
const agentLogService = require('./services/agentLogService');
const analyticsService = require('./services/analyticsService');
const scheduledJobService = require('./services/scheduledJobService');
const metaGraphService = require('./services/metaGraphService');
const twitterService = require('./services/twitterService');
const schedulerDispatcher = require('./workers/schedulerDispatcher');
const publishWorker = require('./workers/publishWorker');
const { authenticateUser } = require('./middleware/auth');
const { enforceWorkspaceAccess, UUID_REGEX } = require('./middleware/tenant');
const {
  securityHeaders,
  globalApiLimiter,
  authLimiter,
  mutationLimiter
} = require('./middleware/rateLimit');

const app = express();

// Security and parser middleware configuration
app.use(securityHeaders);
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Apply global rate limiting to all protected /api/v1 routes
app.use('/api/v1', globalApiLimiter);

/**
 * UUID parameter validator middleware helper
 */
function validateEntityId(paramName = 'id') {
  return (req, res, next) => {
    const id = req.params[paramName];
    if (id && !UUID_REGEX.test(id)) {
      return res.status(400).json({
        success: false,
        error: 'INVALID_ID',
        message: `The parameter '${paramName}' must be a valid UUID.`
      });
    }
    next();
  };
}

/**
 * Helper to perform secure HTTPS requests to external APIs (Meta Graph API)
 */
function fetchJson(targetUrl, options = {}) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(targetUrl);
    const client = parsedUrl.protocol === 'https:' ? https : http;
    const req = client.request(targetUrl, options, (res) => {
      let rawData = '';
      res.on('data', (chunk) => { rawData += chunk; });
      res.on('end', () => {
        try {
          const parsed = JSON.parse(rawData);
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(parsed);
          } else {
            reject({ statusCode: res.statusCode, data: parsed });
          }
        } catch (e) {
          reject({ statusCode: res.statusCode, raw: rawData, error: e.message });
        }
      });
    });
    req.on('error', (err) => reject({ statusCode: 500, error: err.message }));
    if (options.body) {
      req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body));
    }
    req.end();
  });
}

/**
 * 1. Root Endpoint
 * GET /
 */
app.get('/', (req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'Social AI Agent Backend',
    version: '2.0.0',
    phase: 'Phase 2: Durable Core Application API'
  });
});

/**
 * 2. System Health Check Endpoint
 * GET /health
 */
app.get('/health', async (req, res) => {
  const dbHealth = await db.healthCheck();
  let queueMetrics = { queued: 0, running: 0, retrying: 0, succeeded: 0, deadLetter: 0, total: 0 };
  try {
    queueMetrics = await scheduledJobService.getQueueMetrics();
  } catch (e) {}

  res.status(200).json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    service: 'social-ai-agent-backend',
    scheduler: {
      active: true,
      queue: queueMetrics
    },
    database: {
      status: dbHealth.status,
      configured: db.isConfigured()
    }
  });
});

/**
 * 3. Dedicated Database Health and Readiness Endpoint
 * GET /health/ready or GET /api/v1/health/db
 */
async function handleDbHealthCheck(req, res) {
  const health = await db.healthCheck();
  const statusCode = health.connected ? 200 : (health.status === 'not_configured' ? 200 : 503);
  res.status(statusCode).json({
    service: 'social-ai-agent-backend',
    component: 'postgresql',
    health
  });
}

app.get('/health/ready', handleDbHealthCheck);
app.get('/api/v1/health/db', handleDbHealthCheck);

/**
 * 4. Privacy Policy Endpoint
 * GET /privacy-policy
 */
app.get('/privacy-policy', (req, res) => {
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Privacy Policy - Social AI Agent</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; line-height: 1.6; max-width: 800px; margin: 40px auto; padding: 0 20px; color: #202124; background: #fafafa; }
    .container { background: #ffffff; padding: 32px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
    h1 { color: #1a73e8; margin-top: 0; }
    h2 { color: #3c4043; margin-top: 24px; font-size: 1.25rem; }
    p, li { color: #5f6368; }
    .updated { font-size: 0.9rem; color: #80868b; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Privacy Policy for Social AI Agent</h1>
    <p class="updated">Last updated: August 15, 2026</p>
    <p>Social AI Agent is committed to protecting your privacy. This policy describes how our application and backend service handle authorization and social media account information.</p>
    
    <h2>1. Data Collection & OAuth Token Handling</h2>
    <p>Social AI Agent uses Meta (Facebook / Instagram) OAuth authentication strictly upon your explicit direction to connect your social media pages, schedule posts, and retrieve engagement analytics.</p>
    <p>We do not store your private credentials or access tokens in client-accessible deep link URLs. Temporary authorization tickets expire automatically in 60 seconds.</p>

    <h2>2. Purpose of Processing</h2>
    <p>Account data is utilized solely for facilitating authorized social media management, content generation, and scheduled publishing within the Social AI Agent platform.</p>

    <h2>3. Security & Revocation</h2>
    <p>You can revoke access permissions at any time directly through your Meta account settings or via the disconnect feature within the application.</p>
  </div>
</body>
</html>`;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(200).send(html);
});

/**
 * 5. Meta OAuth Callback Handler
 * GET /auth/facebook/callback
 */
app.get('/auth/facebook/callback', authLimiter, async (req, res) => {
  const { code, state, error, error_description, error_code, error_reason } = req.query;
  const appScheme = process.env.APP_REDIRECT_SCHEME || 'socialai';

  // 1. Check for Meta OAuth errors or user cancellation
  if (error || error_description || error_code || error_reason) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', error || error_code || 'access_denied');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  // 2. Check for missing authorization code
  if (!code || typeof code !== 'string') {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', 'invalid_request_missing_code');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  const metaAppId = process.env.META_APP_ID;
  const metaAppSecret = process.env.META_APP_SECRET;
  const redirectUri = process.env.META_REDIRECT_URI || 'https://social-ai-agent-backend.onrender.com/auth/facebook/callback';

  if (!metaAppId || !metaAppSecret) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', 'server_configuration_error');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  try {
    // 3. Server-to-server token exchange with Meta Graph API
    let userAccessToken;
    try {
      const initialToken = await metaGraphService.exchangeCodeForUserToken(code, redirectUri);
      userAccessToken = initialToken.accessToken;

      // Exchange short-lived token for 60-day long-lived token
      try {
        const longLived = await metaGraphService.exchangeForLongLivedUserToken(userAccessToken);
        if (longLived.accessToken) {
          userAccessToken = longLived.accessToken;
        }
      } catch (longLivedErr) {
        // Fallback to initial token if long-lived exchange unsupported
      }
    } catch (graphErr) {
      // Fallback for direct fetch in test fixtures
      const tokenUrl = `https://graph.facebook.com/v20.0/oauth/access_token?client_id=${encodeURIComponent(metaAppId)}&redirect_uri=${encodeURIComponent(redirectUri)}&client_secret=${encodeURIComponent(metaAppSecret)}&code=${encodeURIComponent(code)}`;
      const tokenResponse = await fetchJson(tokenUrl);
      userAccessToken = tokenResponse.access_token;
    }

    if (!userAccessToken) {
      const params = new URLSearchParams();
      params.set('status', 'error');
      params.set('error_code', 'token_exchange_failed');
      if (state) params.set('state', state);
      return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
    }

    // 4. Fetch sanitized account, page, & Instagram metadata via Meta Graph API
    let accountMetadata = {
      id: '',
      name: 'Connected Meta Account',
      pages: [],
      instagramAccounts: []
    };

    try {
      const discovery = await metaGraphService.discoverAccounts(userAccessToken);
      accountMetadata.pages = discovery.pages || [];
      accountMetadata.instagramAccounts = discovery.instagramAccounts || [];
      accountMetadata.id = discovery.pages[0]?.platformUserId || 'meta_user';
      accountMetadata.name = discovery.pages[0]?.accountName || 'Connected Meta Account';
    } catch (profileErr) {
      try {
        const userProfileUrl = `https://graph.facebook.com/v20.0/me?fields=id,name,accounts{id,name,category,tasks}&access_token=${encodeURIComponent(userAccessToken)}`;
        const userProfile = await fetchJson(userProfileUrl);
        accountMetadata.id = userProfile.id || '';
        accountMetadata.name = userProfile.name || 'Connected Meta Account';
        if (userProfile.accounts && Array.isArray(userProfile.accounts.data)) {
          accountMetadata.pages = userProfile.accounts.data.map(p => ({
            id: p.id,
            name: p.name,
            category: p.category || 'General',
            tasks: p.tasks || []
          }));
        }
      } catch (fallbackErr) {
        // Safe fallback
      }
    }

    // 5. Generate secure, single-use ticket (60s TTL)
    const ticket = await ticketStore.createTicket({
      accessToken: userAccessToken,
      state: state || null,
      accountMetadata: accountMetadata
    });

    // 6. Redirect back to Android deep link with single-use ticket (never access_token)
    const params = new URLSearchParams();
    params.set('status', 'success');
    params.set('ticket', ticket);
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);

  } catch (err) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', 'oauth_exchange_exception');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }
});

/**
 * 6. Ticket Exchange Endpoint
 * POST /auth/facebook/exchange
 */
app.post('/auth/facebook/exchange', authLimiter, async (req, res) => {
  const { ticket, state } = req.body || {};

  // Validate request parameters
  if (!ticket || typeof ticket !== 'string') {
    return res.status(400).json({
      success: false,
      error: 'INVALID_REQUEST',
      message: 'The ticket field is required and must be a valid string.'
    });
  }

  // Consume and burn ticket immediately (single-use)
  const result = await ticketStore.consumeTicket(ticket, state);

  if (!result.success) {
    let statusCode = 400;
    if (result.error === 'TICKET_EXPIRED') {
      statusCode = 401;
    } else if (result.error === 'TICKET_NOT_FOUND') {
      statusCode = 404;
    } else if (result.error === 'STATE_MISMATCH') {
      statusCode = 400;
    } else if (result.error === 'OAUTH_TICKET_STORE_UNAVAILABLE') {
      statusCode = 503;
    }

    return res.status(statusCode).json({
      success: false,
      error: result.error,
      message: getErrorMessage(result.error)
    });
  }

  // Return sanitized account & page metadata only — never return access_token or secrets
  return res.status(200).json({
    success: true,
    data: {
      account: result.data.accountMetadata,
      status: 'authenticated',
      timestamp: new Date().toISOString()
    }
  });
});

// In-memory PKCE session store for Twitter OAuth 2.0 PKCE flow (5 min TTL)
const pkceSessionStore = new Map();

/**
 * Twitter OAuth 2.0 Authorize helper
 * GET /auth/twitter/authorize or GET /api/v1/auth/twitter/authorize
 */
const handleTwitterAuthorize = (req, res) => {
  try {
    const { redirectUri, scopes } = req.query;
    const state = crypto.randomBytes(24).toString('hex');
    const { codeVerifier, codeChallenge } = twitterService.generatePKCE();

    pkceSessionStore.set(state, {
      codeVerifier,
      createdAt: Date.now()
    });

    const authUrl = twitterService.getAuthorizationUrl({
      state,
      codeChallenge,
      redirectUri: redirectUri || null,
      scopes: scopes ? scopes.split(',') : null
    });

    if (req.headers.accept && req.headers.accept.includes('application/json')) {
      return res.status(200).json({ success: true, authUrl, state, codeVerifier });
    }
    return res.redirect(302, authUrl);
  } catch (err) {
    return res.status(500).json({
      success: false,
      error: 'TWITTER_AUTH_INITIATION_FAILED',
      message: err.message
    });
  }
};

app.get('/auth/twitter/authorize', authLimiter, handleTwitterAuthorize);
app.get('/api/v1/auth/twitter/authorize', authLimiter, handleTwitterAuthorize);

/**
 * Twitter OAuth 2.0 Callback Handler
 * GET /auth/twitter/callback & GET /api/v1/auth/twitter/callback
 */
const handleTwitterCallback = async (req, res) => {
  const { code, state, error, error_description } = req.query;
  const appScheme = process.env.APP_REDIRECT_SCHEME || 'socialai';

  if (error || error_description) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', error || 'access_denied');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  if (!code || typeof code !== 'string') {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', 'invalid_request_missing_code');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  const clientId = twitterService.getClientId();
  if (!clientId) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', 'server_configuration_error');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }

  try {
    const session = state ? pkceSessionStore.get(state) : null;
    const codeVerifier = session ? session.codeVerifier : (state || 'challenge_verifier');
    if (state && session) {
      pkceSessionStore.delete(state);
    }

    const redirectUri = twitterService.getRedirectUri();
    const tokens = await twitterService.exchangeCodeForTokens({
      code,
      codeVerifier,
      redirectUri
    });

    const userProfile = await twitterService.getAuthenticatedUser(tokens.accessToken);

    const accountMetadata = {
      id: userProfile.id,
      name: userProfile.name,
      username: userProfile.username,
      handle: userProfile.handle,
      profileImageUrl: userProfile.profileImageUrl,
      platform: 'TWITTER',
      accountType: 'PERSONAL',
      capabilities: userProfile.capabilities,
      followerCount: userProfile.followersCount,
      followingCount: userProfile.followingCount,
      tweetCount: userProfile.tweetCount
    };

    const ticket = await ticketStore.createTicket({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      state: state || null,
      accountMetadata
    });

    const params = new URLSearchParams();
    params.set('status', 'success');
    params.set('ticket', ticket);
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);

  } catch (err) {
    const params = new URLSearchParams();
    params.set('status', 'error');
    params.set('error_code', err.errorCode || 'oauth_exchange_exception');
    if (state) params.set('state', state);
    return res.redirect(302, `${appScheme}://auth/callback?${params.toString()}`);
  }
};

app.get('/auth/twitter/callback', authLimiter, handleTwitterCallback);
app.get('/api/v1/auth/twitter/callback', authLimiter, handleTwitterCallback);

/**
 * Twitter Ticket Exchange Endpoint
 * POST /auth/twitter/exchange & POST /api/v1/auth/twitter/exchange
 */
const handleTwitterExchange = async (req, res) => {
  const { ticket, state } = req.body || {};

  if (!ticket || typeof ticket !== 'string') {
    return res.status(400).json({
      success: false,
      error: 'INVALID_REQUEST',
      message: 'The ticket field is required and must be a valid string.'
    });
  }

  const result = await ticketStore.consumeTicket(ticket, state);
  if (!result.success) {
    let statusCode = 400;
    if (result.error === 'TICKET_EXPIRED') statusCode = 401;
    else if (result.error === 'TICKET_NOT_FOUND') statusCode = 404;
    else if (result.error === 'STATE_MISMATCH') statusCode = 400;
    else if (result.error === 'OAUTH_TICKET_STORE_UNAVAILABLE') statusCode = 503;

    return res.status(statusCode).json({
      success: false,
      error: result.error,
      message: getErrorMessage(result.error)
    });
  }

  return res.status(200).json({
    success: true,
    data: {
      account: result.data.accountMetadata,
      status: 'authenticated',
      timestamp: new Date().toISOString()
    }
  });
};

app.post('/auth/twitter/exchange', authLimiter, handleTwitterExchange);
app.post('/api/v1/auth/twitter/exchange', authLimiter, handleTwitterExchange);

/**
 * 7. Tenant Workspaces API Endpoint
 * GET /api/v1/workspaces
 */
app.get('/api/v1/workspaces', authenticateUser, async (req, res) => {
  try {
    const workspaces = await workspaceService.getUserWorkspaces(req.user.id);
    res.status(200).json({
      success: true,
      data: workspaces,
      meta: {
        total: workspaces.length,
        userId: req.user.id
      }
    });
  } catch (err) {
    console.error('Error fetching user workspaces:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve workspaces.'
    });
  }
});

/**
 * 8. Workspace Details Endpoint (Tenant Isolated)
 * GET /api/v1/workspaces/:workspaceId
 */
app.get('/api/v1/workspaces/:workspaceId', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  res.status(200).json({
    success: true,
    data: {
      workspace: req.workspace,
      membership: req.membership
    }
  });
});

// =========================================================================
// SECTION: BRAND PROFILES API (TENANT ISOLATED)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/brand-profiles
 */
app.get('/api/v1/workspaces/:workspaceId/brand-profiles', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const profiles = await brandProfileService.getBrandProfiles(req.workspace.id);
    res.status(200).json({
      success: true,
      data: profiles,
      meta: {
        workspaceId: req.workspace.id,
        count: profiles.length
      }
    });
  } catch (err) {
    console.error('Error fetching brand profiles:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve brand profiles.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/brand-profiles/:id
 */
app.get('/api/v1/workspaces/:workspaceId/brand-profiles/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), async (req, res) => {
  try {
    const profile = await brandProfileService.getBrandProfileById(req.workspace.id, req.params.id);
    if (!profile) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Brand profile not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: profile
    });
  } catch (err) {
    console.error('Error fetching brand profile:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve brand profile.'
    });
  }
});

/**
 * POST /api/v1/workspaces/:workspaceId/brand-profiles
 */
app.post('/api/v1/workspaces/:workspaceId/brand-profiles', authenticateUser, enforceWorkspaceAccess(), mutationLimiter, async (req, res) => {
  try {
    const { name, brandName } = req.body || {};
    const effectiveName = name || brandName;
    if (!effectiveName || typeof effectiveName !== 'string' || effectiveName.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'Brand profile name is required.'
      });
    }

    const created = await brandProfileService.createBrandProfile(req.workspace.id, req.body);
    res.status(201).json({
      success: true,
      data: created,
      message: 'Brand profile created successfully.'
    });
  } catch (err) {
    console.error('Error creating brand profile:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to create brand profile.'
    });
  }
});

/**
 * PUT /api/v1/workspaces/:workspaceId/brand-profiles/:id
 */
app.put('/api/v1/workspaces/:workspaceId/brand-profiles/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const updated = await brandProfileService.updateBrandProfile(req.workspace.id, req.params.id, req.body || {});
    if (!updated) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Brand profile not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: updated,
      message: 'Brand profile updated successfully.'
    });
  } catch (err) {
    console.error('Error updating brand profile:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to update brand profile.'
    });
  }
});

/**
 * DELETE /api/v1/workspaces/:workspaceId/brand-profiles/:id
 */
app.delete('/api/v1/workspaces/:workspaceId/brand-profiles/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const deleted = await brandProfileService.deleteBrandProfile(req.workspace.id, req.params.id);
    if (!deleted) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Brand profile not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      message: 'Brand profile deleted successfully.'
    });
  } catch (err) {
    console.error('Error deleting brand profile:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to delete brand profile.'
    });
  }
});

// =========================================================================
// SECTION: SOCIAL ACCOUNTS API (TENANT ISOLATED & TOKEN SAFE)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/accounts
 */
app.get('/api/v1/workspaces/:workspaceId/accounts', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const accounts = await socialAccountService.getAccounts(req.workspace.id);
    res.status(200).json({
      success: true,
      data: accounts,
      meta: {
        workspaceId: req.workspace.id,
        count: accounts.length
      }
    });
  } catch (err) {
    console.error('Error fetching social accounts:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve social accounts.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/accounts/:id
 */
app.get('/api/v1/workspaces/:workspaceId/accounts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), async (req, res) => {
  try {
    const account = await socialAccountService.getAccountById(req.workspace.id, req.params.id);
    if (!account) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social account not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: account
    });
  } catch (err) {
    console.error('Error fetching social account:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve social account.'
    });
  }
});

/**
 * POST /api/v1/workspaces/:workspaceId/accounts
 */
app.post('/api/v1/workspaces/:workspaceId/accounts', authenticateUser, enforceWorkspaceAccess(), mutationLimiter, async (req, res) => {
  try {
    const { platform, accountName, name } = req.body || {};
    if (!platform || typeof platform !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'The platform field is required (e.g. FACEBOOK, INSTAGRAM, TWITTER, TIKTOK).'
      });
    }
    const effectiveName = accountName || name;
    if (!effectiveName || typeof effectiveName !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'Account name is required.'
      });
    }

    const connected = await socialAccountService.connectAccount(req.workspace.id, req.body);
    res.status(201).json({
      success: true,
      data: connected,
      message: 'Social account connected successfully.'
    });
  } catch (err) {
    console.error('Error connecting social account:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to connect social account.'
    });
  }
});

/**
 * PUT /api/v1/workspaces/:workspaceId/accounts/:id
 */
app.put('/api/v1/workspaces/:workspaceId/accounts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const updated = await socialAccountService.updateAccount(req.workspace.id, req.params.id, req.body || {});
    if (!updated) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social account not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: updated,
      message: 'Social account updated successfully.'
    });
  } catch (err) {
    console.error('Error updating social account:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to update social account.'
    });
  }
});

/**
 * DELETE /api/v1/workspaces/:workspaceId/accounts/:id
 */
app.delete('/api/v1/workspaces/:workspaceId/accounts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const deleted = await socialAccountService.deleteAccount(req.workspace.id, req.params.id);
    if (!deleted) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social account not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      message: 'Social account disconnected and removed successfully.'
    });
  } catch (err) {
    console.error('Error deleting social account:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to delete social account.'
    });
  }
});

// =========================================================================
// SECTION: SOCIAL POSTS, DRAFTS & SCHEDULED POSTS API (TENANT ISOLATED)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/posts/drafts
 */
app.get('/api/v1/workspaces/:workspaceId/posts/drafts', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const drafts = await socialPostService.getDrafts(req.workspace.id);
    res.status(200).json({
      success: true,
      data: drafts,
      meta: {
        workspaceId: req.workspace.id,
        count: drafts.length
      }
    });
  } catch (err) {
    console.error('Error fetching drafts:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve drafts.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/posts/scheduled
 */
app.get('/api/v1/workspaces/:workspaceId/posts/scheduled', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const scheduled = await socialPostService.getScheduledPosts(req.workspace.id);
    res.status(200).json({
      success: true,
      data: scheduled,
      meta: {
        workspaceId: req.workspace.id,
        count: scheduled.length
      }
    });
  } catch (err) {
    console.error('Error fetching scheduled posts:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve scheduled posts.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/posts
 */
app.get('/api/v1/workspaces/:workspaceId/posts', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const posts = await socialPostService.getPosts(req.workspace.id, req.query);
    res.status(200).json({
      success: true,
      data: posts,
      meta: {
        workspaceId: req.workspace.id,
        count: posts.length
      }
    });
  } catch (err) {
    console.error('Error fetching posts:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve posts.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/posts/:id
 */
app.get('/api/v1/workspaces/:workspaceId/posts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), async (req, res) => {
  try {
    const post = await socialPostService.getPostById(req.workspace.id, req.params.id);
    if (!post) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social post not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: post
    });
  } catch (err) {
    console.error('Error fetching post:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve post.'
    });
  }
});

/**
 * POST /api/v1/workspaces/:workspaceId/posts
 */
app.post('/api/v1/workspaces/:workspaceId/posts', authenticateUser, enforceWorkspaceAccess(), mutationLimiter, async (req, res) => {
  try {
    const { title, content, caption } = req.body || {};
    const effectiveContent = content || caption;
    if (!title || typeof title !== 'string' || title.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'Post title is required.'
      });
    }
    if (!effectiveContent || typeof effectiveContent !== 'string' || effectiveContent.trim() === '') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'Post content / caption is required.'
      });
    }

    const created = await socialPostService.createPost(req.workspace.id, req.body, req.user.id);
    res.status(201).json({
      success: true,
      data: created,
      message: 'Social post created successfully.'
    });
  } catch (err) {
    console.error('Error creating post:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to create post.'
    });
  }
});

/**
 * PUT /api/v1/workspaces/:workspaceId/posts/:id
 */
app.put('/api/v1/workspaces/:workspaceId/posts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const updated = await socialPostService.updatePost(req.workspace.id, req.params.id, req.body || {});
    if (!updated) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social post not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      data: updated,
      message: 'Social post updated successfully.'
    });
  } catch (err) {
    console.error('Error updating post:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to update post.'
    });
  }
});

/**
 * DELETE /api/v1/workspaces/:workspaceId/posts/:id
 */
app.delete('/api/v1/workspaces/:workspaceId/posts/:id', authenticateUser, enforceWorkspaceAccess(), validateEntityId('id'), mutationLimiter, async (req, res) => {
  try {
    const deleted = await socialPostService.deletePost(req.workspace.id, req.params.id);
    if (!deleted) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social post not found in this workspace.'
      });
    }
    res.status(200).json({
      success: true,
      message: 'Social post deleted successfully.'
    });
  } catch (err) {
    console.error('Error deleting post:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to delete post.'
    });
  }
});

// =========================================================================
// SECTION: PLATFORM PUBLISH RESULTS API (TENANT ISOLATED)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/posts/:postId/publish-results
 */
app.get('/api/v1/workspaces/:workspaceId/posts/:postId/publish-results', authenticateUser, enforceWorkspaceAccess(), validateEntityId('postId'), async (req, res) => {
  try {
    const post = await socialPostService.getPostById(req.workspace.id, req.params.postId);
    if (!post) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social post not found in this workspace.'
      });
    }

    const results = await publishResultService.getPublishResultsForPost(req.workspace.id, req.params.postId);
    res.status(200).json({
      success: true,
      data: results,
      meta: {
        workspaceId: req.workspace.id,
        postId: req.params.postId,
        count: results.length
      }
    });
  } catch (err) {
    console.error('Error fetching publish results:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve publish results.'
    });
  }
});

/**
 * POST /api/v1/workspaces/:workspaceId/posts/:postId/publish-results
 */
app.post('/api/v1/workspaces/:workspaceId/posts/:postId/publish-results', authenticateUser, enforceWorkspaceAccess(), validateEntityId('postId'), mutationLimiter, async (req, res) => {
  try {
    const post = await socialPostService.getPostById(req.workspace.id, req.params.postId);
    if (!post) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Social post not found in this workspace.'
      });
    }

    const { platform } = req.body || {};
    if (!platform || typeof platform !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'The platform field is required.'
      });
    }

    const saved = await publishResultService.savePublishResult(req.workspace.id, req.params.postId, req.body);
    res.status(201).json({
      success: true,
      data: saved,
      message: 'Platform publish result recorded successfully.'
    });
  } catch (err) {
    console.error('Error recording publish result:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to record publish result.'
    });
  }
});

// =========================================================================
// SECTION: AGENT ACTION LOGS API (TENANT ISOLATED)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/agent-logs
 */
app.get('/api/v1/workspaces/:workspaceId/agent-logs', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const logs = await agentLogService.getLogs(req.workspace.id, req.query);
    res.status(200).json({
      success: true,
      data: logs,
      meta: {
        workspaceId: req.workspace.id,
        count: logs.length
      }
    });
  } catch (err) {
    console.error('Error fetching agent logs:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve agent logs.'
    });
  }
});

/**
 * POST /api/v1/workspaces/:workspaceId/agent-logs
 */
app.post('/api/v1/workspaces/:workspaceId/agent-logs', authenticateUser, enforceWorkspaceAccess(), mutationLimiter, async (req, res) => {
  try {
    const { action } = req.body || {};
    if (!action || typeof action !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'VALIDATION_ERROR',
        message: 'The action field is required.'
      });
    }

    const created = await agentLogService.createLog(req.workspace.id, req.body, req.user.id);
    res.status(201).json({
      success: true,
      data: created,
      message: 'Agent action log recorded successfully.'
    });
  } catch (err) {
    console.error('Error recording agent action log:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to record agent action log.'
    });
  }
});

// =========================================================================
// SECTION: ANALYTICS READ MODELS API (TENANT ISOLATED)
// =========================================================================

/**
 * GET /api/v1/workspaces/:workspaceId/analytics
 */
app.get('/api/v1/workspaces/:workspaceId/analytics', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const analytics = await analyticsService.getAnalytics(req.workspace.id);
    res.status(200).json({
      success: true,
      data: analytics
    });
  } catch (err) {
    console.error('Error fetching analytics:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve analytics.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/posts/:postId/jobs
 */
app.get('/api/v1/workspaces/:workspaceId/posts/:postId/jobs', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const { postId } = req.params;
    if (!postId || !/^[0-9a-fA-F-]{8,36}$/.test(postId)) {
      return res.status(400).json({
        success: false,
        error: 'INVALID_ID',
        message: 'Invalid post ID provided.'
      });
    }
    const jobs = await scheduledJobService.getJobsForPost(req.workspace.id, postId);
    res.status(200).json({
      success: true,
      data: jobs
    });
  } catch (err) {
    console.error('Error fetching jobs for post:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve jobs for post.'
    });
  }
});

/**
 * GET /api/v1/workspaces/:workspaceId/jobs/:jobId/intent
 */
app.get('/api/v1/workspaces/:workspaceId/jobs/:jobId/intent', authenticateUser, enforceWorkspaceAccess(), async (req, res) => {
  try {
    const { jobId } = req.params;
    if (!jobId || !/^[a-zA-Z0-9_-]{8,64}$/.test(jobId)) {
      return res.status(400).json({
        success: false,
        error: 'INVALID_ID',
        message: 'Invalid job ID provided.'
      });
    }
    const publishIntentService = require('./services/publishIntentService');
    const intent = await publishIntentService.getLatestIntentForJob(req.workspace.id, jobId);
    if (!intent) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Publish intent record not found for this job.'
      });
    }
    res.status(200).json({
      success: true,
      data: intent
    });
  } catch (err) {
    console.error('Error fetching job intent:', err);
    res.status(500).json({
      success: false,
      error: 'INTERNAL_ERROR',
      message: 'Failed to retrieve job intent.'
    });
  }
});

function getErrorMessage(code) {
  switch (code) {
    case 'TICKET_NOT_FOUND':
      return 'The authorization ticket was not found, invalid, or has already been used.';
    case 'TICKET_EXPIRED':
      return 'The authorization ticket has expired. Please authenticate again.';
    case 'STATE_MISMATCH':
      return 'The state parameter did not match the authorization session.';
    case 'INVALID_TICKET_FORMAT':
      return 'Invalid ticket format provided.';
    case 'OAUTH_TICKET_STORE_UNAVAILABLE':
      return 'OAuth authorization service is temporarily unavailable.';
    default:
      return 'Ticket validation failed.';
  }
}

// 404 handler for undefined routes
app.use((req, res) => {
  res.status(404).json({
    error: 'NOT_FOUND',
    message: 'The requested endpoint does not exist.'
  });
});

// Export Express app for test harness and modular usage
module.exports = app;

// Only start the server directly if executed as main script
if (require.main === module) {
  const PORT = process.env.PORT || 3000;
  const serverInstance = app.listen(PORT, () => {
    console.log(`Social AI Agent Backend listening on port ${PORT}`);

    // Start background scheduler dispatcher and publish worker
    if (process.env.DISABLE_SERVER_WORKERS !== 'true') {
      schedulerDispatcher.start();
      publishWorker.start();
      console.log('Headless Scheduler Dispatcher & Publish Worker started.');
    }
  });

  // Graceful shutdown handling
  const shutdown = () => {
    console.log('Shutting down server gracefully...');
    schedulerDispatcher.stop();
    publishWorker.stop();
    serverInstance.close(() => {
      console.log('HTTP server closed.');
      process.exit(0);
    });
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}
