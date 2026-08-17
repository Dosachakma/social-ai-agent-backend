const crypto = require('crypto');

/**
 * Real X / Twitter API v2 Integration Service.
 * 
 * Provides:
 * 1. OAuth 2.0 PKCE Generation (code_verifier and code_challenge).
 * 2. OAuth 2.0 Authorization URL generation with required scopes.
 * 3. OAuth 2.0 code exchange for User Access Token + Refresh Token (offline.access).
 * 4. Token Refresh handling (refresh_token flow).
 * 5. Authenticated User Profile & Metrics discovery (/2/users/me).
 * 6. Real Tweet Publishing (POST /2/tweets).
 * 7. Ambiguous state reconciliation via recent user tweets lookup.
 * 8. Comprehensive Twitter API v2 error classification & rate-limiting analysis.
 * 
 * Security Invariant:
 * TWITTER_CLIENT_SECRET, refresh tokens, and raw access tokens are strictly server-side.
 */
class TwitterService {
  constructor() {
    this.baseUrl = 'https://api.twitter.com/2';
    this.authBaseUrl = 'https://twitter.com/i/oauth2/authorize';
    this.tokenUrl = 'https://api.twitter.com/2/oauth2/token';
  }

  getClientId() {
    return process.env.TWITTER_CLIENT_ID || process.env.X_CLIENT_ID || '';
  }

  getClientSecret() {
    return process.env.TWITTER_CLIENT_SECRET || process.env.X_CLIENT_SECRET || '';
  }

  getRedirectUri() {
    return process.env.TWITTER_REDIRECT_URI || process.env.X_REDIRECT_URI || 'https://social-ai-agent-backend.onrender.com/auth/twitter/callback';
  }

  /**
   * 1. Generate PKCE code verifier and code challenge (S256).
   */
  generatePKCE() {
    const codeVerifier = crypto.randomBytes(32).toString('base64url');
    const codeChallenge = crypto
      .createHash('sha256')
      .update(codeVerifier)
      .digest('base64url');
    return {
      codeVerifier,
      codeChallenge,
      codeChallengeMethod: 'S256'
    };
  }

  /**
   * 2. Generate Twitter OAuth 2.0 Authorization URL.
   */
  getAuthorizationUrl({ state, codeChallenge, redirectUri = null, scopes = null }) {
    const clientId = this.getClientId();
    if (!clientId) {
      const err = new Error('Twitter Client ID is not configured (TWITTER_CLIENT_ID)');
      err.errorCode = 'SERVER_CONFIGURATION_ERROR';
      err.category = 'PERMANENT';
      throw err;
    }

    const targetRedirectUri = redirectUri || this.getRedirectUri();
    const defaultScopes = ['tweet.read', 'tweet.write', 'users.read', 'offline.access'];
    const activeScopes = scopes || defaultScopes;
    const scopeParam = encodeURIComponent(activeScopes.join(' '));

    return `${this.authBaseUrl}?response_type=code` +
      `&client_id=${encodeURIComponent(clientId)}` +
      `&redirect_uri=${encodeURIComponent(targetRedirectUri)}` +
      `&scope=${scopeParam}` +
      `&state=${encodeURIComponent(state)}` +
      `&code_challenge=${encodeURIComponent(codeChallenge)}` +
      `&code_challenge_method=S256`;
  }

  /**
   * Helper for Twitter API HTTP calls with rate limit and error handling.
   */
  async request(url, options = {}) {
    const method = options.method || 'GET';
    const headers = {
      'Accept': 'application/json',
      ...(options.headers || {})
    };

    let body = options.body;
    if (body && typeof body === 'object' && !(body instanceof URLSearchParams)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }

    const response = await fetch(url, {
      method,
      headers,
      body: method !== 'GET' && method !== 'HEAD' ? body : undefined,
      signal: options.timeout ? AbortSignal.timeout(options.timeout) : AbortSignal.timeout(30000)
    });

    let data;
    const text = await response.text();
    try {
      data = JSON.parse(text);
    } catch (e) {
      data = { raw: text };
    }

    if (!response.ok) {
      const rateLimitReset = response.headers.get('x-rate-limit-reset');
      const retryAfter = response.headers.get('retry-after');

      const classified = this.classifyTwitterError({
        statusCode: response.status,
        data,
        rateLimitReset,
        retryAfter
      });

      const error = new Error(classified.message);
      error.errorCode = classified.errorCode;
      error.category = classified.category;
      error.isRetryable = classified.isRetryable;
      error.statusCode = response.status;
      error.retryAfter = classified.retryAfter;
      error.rawResponse = data;
      throw error;
    }

    return data;
  }

  /**
   * 3. Exchange authorization code with code_verifier for OAuth 2.0 tokens.
   */
  async exchangeCodeForTokens({ code, codeVerifier, redirectUri = null }) {
    const clientId = this.getClientId();
    const clientSecret = this.getClientSecret();
    const targetRedirectUri = redirectUri || this.getRedirectUri();

    if (!clientId) {
      const err = new Error('Twitter Client ID not configured');
      err.errorCode = 'SERVER_CONFIGURATION_ERROR';
      err.category = 'PERMANENT';
      throw err;
    }

    const params = new URLSearchParams();
    params.append('code', code);
    params.append('grant_type', 'authorization_code');
    params.append('client_id', clientId);
    params.append('redirect_uri', targetRedirectUri);
    params.append('code_verifier', codeVerifier);

    const headers = {
      'Content-Type': 'application/x-www-form-urlencoded'
    };

    // If client secret is provided (confidential client), send Basic Auth header
    if (clientSecret) {
      const authHeader = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
      headers['Authorization'] = `Basic ${authHeader}`;
    }

    const data = await this.request(this.tokenUrl, {
      method: 'POST',
      headers,
      body: params
    });

    return {
      accessToken: data.access_token,
      refreshToken: data.refresh_token || null,
      expiresIn: data.expires_in || 7200,
      scope: data.scope ? data.scope.split(' ') : [],
      tokenType: data.token_type || 'Bearer'
    };
  }

  /**
   * 4. Refresh expired access token using refresh_token.
   */
  async refreshAccessToken(refreshToken) {
    const clientId = this.getClientId();
    const clientSecret = this.getClientSecret();

    if (!clientId) {
      const err = new Error('Twitter Client ID not configured');
      err.errorCode = 'SERVER_CONFIGURATION_ERROR';
      err.category = 'PERMANENT';
      throw err;
    }

    const params = new URLSearchParams();
    params.append('grant_type', 'refresh_token');
    params.append('refresh_token', refreshToken);
    params.append('client_id', clientId);

    const headers = {
      'Content-Type': 'application/x-www-form-urlencoded'
    };

    if (clientSecret) {
      const authHeader = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
      headers['Authorization'] = `Basic ${authHeader}`;
    }

    const data = await this.request(this.tokenUrl, {
      method: 'POST',
      headers,
      body: params
    });

    return {
      accessToken: data.access_token,
      refreshToken: data.refresh_token || refreshToken, // May rotate or keep previous
      expiresIn: data.expires_in || 7200,
      scope: data.scope ? data.scope.split(' ') : [],
      tokenType: data.token_type || 'Bearer'
    };
  }

  /**
   * 5. Discover Authenticated User Profile & Metrics.
   */
  async getAuthenticatedUser(accessToken) {
    const endpoint = `${this.baseUrl}/users/me?user.fields=profile_image_url,description,public_metrics,verified`;
    const data = await this.request(endpoint, {
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });

    const user = data.data || {};
    const metrics = user.public_metrics || {};

    const capabilities = [
      'CREATE_POST',
      'PUBLISH_POST',
      'MEDIA_UPLOAD',
      'READ_ANALYTICS',
      'READ_COMMENTS',
      'REPLY_COMMENT'
    ];

    return {
      id: user.id,
      platformUserId: user.id,
      name: user.name || 'X / Twitter User',
      accountName: user.name || 'X / Twitter User',
      username: user.username || 'twitter_user',
      handle: user.username ? `@${user.username}` : '@twitter_user',
      profileImageUrl: user.profile_image_url || '',
      description: user.description || '',
      followersCount: metrics.followers_count || 0,
      followingCount: metrics.following_count || 0,
      tweetCount: metrics.tweet_count || 0,
      isVerified: Boolean(user.verified),
      capabilities,
      accountType: 'PERSONAL',
      connectionStatus: 'CONNECTED',
      tokenStatus: 'VALID'
    };
  }

  /**
   * Alias for discoverAccount matching platform service standards.
   */
  async discoverAccount(accessToken) {
    return await this.getAuthenticatedUser(accessToken);
  }

  /**
   * 6. Real Tweet Publishing (POST /2/tweets).
   * Supports text tweets up to 280 characters and media attachments if available.
   */
  async publishTweet({ accessToken, text, mediaIds = [], quoteTweetId = null, replySettings = null }) {
    if (!accessToken) {
      const err = new Error('Twitter access token is required for publishing');
      err.errorCode = 'INVALID_PARAMETER';
      err.category = 'PERMANENT';
      throw err;
    }

    if (!text || text.trim().length === 0) {
      const err = new Error('Tweet text cannot be empty');
      err.errorCode = 'INVALID_PARAMETER';
      err.category = 'PERMANENT';
      throw err;
    }

    const payload = {
      text: text.trim()
    };

    if (mediaIds && mediaIds.length > 0) {
      payload.media = { media_ids: mediaIds };
    }

    if (quoteTweetId) {
      payload.quote_tweet_id = quoteTweetId;
    }

    if (replySettings) {
      payload.reply_settings = replySettings;
    }

    const endpoint = `${this.baseUrl}/tweets`;
    const result = await this.request(endpoint, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: payload
    });

    const tweetData = result.data || {};
    const tweetId = tweetData.id || result.id;

    return {
      externalPostId: tweetId,
      externalPostUrl: `https://x.com/i/web/status/${tweetId}`,
      text: tweetData.text || text,
      rawResponse: result
    };
  }

  /**
   * 7. Reconcile Twitter Post during Ambiguous Network State.
   * Fetches recent tweets for the user to determine if the tweet was already created.
   */
  async reconcileTweet({ accessToken, text, userId }) {
    try {
      if (!userId) {
        const me = await this.getAuthenticatedUser(accessToken);
        userId = me.id;
      }

      const endpoint = `${this.baseUrl}/users/${userId}/tweets?max_results=5&tweet.fields=created_at,text`;
      const result = await this.request(endpoint, {
        headers: {
          'Authorization': `Bearer ${accessToken}`
        }
      });

      const tweets = result.data || [];
      if (!text || tweets.length === 0) {
        return { status: 'NOT_FOUND' };
      }

      const cleanTarget = text.trim().toLowerCase();
      const match = tweets.find(t => {
        if (!t.text) return false;
        const cleanT = t.text.trim().toLowerCase();
        return cleanT === cleanTarget || (cleanTarget.length > 20 && cleanT.startsWith(cleanTarget.slice(0, 50)));
      });

      if (match) {
        return {
          status: 'FOUND',
          externalPostId: match.id,
          externalPostUrl: `https://x.com/i/web/status/${match.id}`
        };
      }

      return { status: 'NOT_FOUND' };
    } catch (err) {
      if (err.category === 'PERMANENT') throw err;
      return { status: 'INDETERMINATE', error: err.message };
    }
  }

  /**
   * 8. Error Taxonomy Classifier for X / Twitter API v2.
   */
  classifyTwitterError({ statusCode, data = {}, message = '', rateLimitReset, retryAfter }) {
    const title = data.title || data.error || '';
    const detail = data.detail || data.error_description || data.message || message || '';
    const fullMsg = detail ? (title && !detail.includes(title) ? `${title}: ${detail}` : detail) : (title || `Twitter HTTP ${statusCode}`);
    const lowerMsg = fullMsg.toLowerCase();

    // 8a. Timeout / Ambiguous (check first for 408, 504, or explicit timeout keyword)
    if (statusCode === 408 || statusCode === 504 || lowerMsg.includes('timeout') || lowerMsg.includes('etimedout')) {
      return {
        errorCode: 'AMBIGUOUS_TIMEOUT',
        category: 'AMBIGUOUS',
        isRetryable: true,
        message: `Network timeout during Twitter API call: ${fullMsg}`
      };
    }

    // 8b. Authentication & Token Revocation (401)
    if (statusCode === 401) {
      return {
        errorCode: 'TOKEN_EXPIRED',
        category: 'PERMANENT',
        isRetryable: false,
        message: `X/Twitter token expired or invalid: ${fullMsg}. Re-authentication required.`
      };
    }

    // 8c. Rate Limiting (429)
    if (statusCode === 429) {
      let secondsToWait = 900; // default 15 mins
      if (retryAfter) {
        secondsToWait = parseInt(retryAfter, 10) || 900;
      } else if (rateLimitReset) {
        const resetTime = parseInt(rateLimitReset, 10);
        if (resetTime) {
          const nowSec = Math.floor(Date.now() / 1000);
          secondsToWait = Math.max(1, resetTime - nowSec);
        }
      }

      return {
        errorCode: 'RATE_LIMITED',
        category: 'RATE_LIMIT',
        isRetryable: true,
        retryAfter: secondsToWait,
        message: `X/Twitter API rate limit reached. Retry after ${secondsToWait}s.`
      };
    }

    // 8d. Duplicate Tweet / Forbidden / Permissions (403)
    if (statusCode === 403) {
      if (lowerMsg.includes('duplicate')) {
        return {
          errorCode: 'DUPLICATE_CONTENT',
          category: 'PERMANENT',
          isRetryable: false,
          message: 'X/Twitter rejected duplicate status update. Post content must be unique.'
        };
      }

      if (lowerMsg.includes('suspended') || lowerMsg.includes('locked')) {
        return {
          errorCode: 'ACCOUNT_SUSPENDED',
          category: 'PERMANENT',
          isRetryable: false,
          message: 'X/Twitter account is locked or suspended by platform.'
        };
      }

      return {
        errorCode: 'FORBIDDEN',
        category: 'PERMANENT',
        isRetryable: false,
        message: `X/Twitter forbidden error: ${fullMsg}`
      };
    }

    // 8e. Content Length / Invalid parameters (400)
    if (statusCode === 400) {
      if (lowerMsg.includes('exceeds limit') || lowerMsg.includes('too long') || lowerMsg.includes('length')) {
        return {
          errorCode: 'TWEET_TOO_LONG',
          category: 'PERMANENT',
          isRetryable: false,
          message: 'Tweet character length exceeds limit.'
        };
      }

      return {
        errorCode: 'INVALID_PARAMETER',
        category: 'PERMANENT',
        isRetryable: false,
        message: `Invalid Twitter request parameter: ${fullMsg}`
      };
    }

    // 8f. Transient Server Errors (500-599)
    if (statusCode >= 500 && statusCode < 600) {
      return {
        errorCode: 'TRANSIENT_TWITTER_ERROR',
        category: 'TRANSIENT',
        isRetryable: true,
        message: `X/Twitter server error (${statusCode}): ${fullMsg}`
      };
    }

    return {
      errorCode: 'TWITTER_API_ERROR',
      category: 'TRANSIENT',
      isRetryable: true,
      message: fullMsg
    };
  }
}

module.exports = new TwitterService();
