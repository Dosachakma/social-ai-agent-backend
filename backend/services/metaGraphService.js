const https = require('https');
const crypto = require('crypto');

/**
 * Meta Graph API Integration Service (Facebook & Instagram Professional).
 * 
 * Provides:
 * 1. OAuth code to short-lived and long-lived token exchange (60-day token).
 * 2. Facebook Page discovery with granular task-based capability detection.
 * 3. Instagram Business/Professional account discovery (distinguishes from unsupported Personal accounts).
 * 4. Facebook Page feed and photo publishing.
 * 5. Instagram 2-step async container creation and media publishing.
 * 6. External Graph API post lookup for headless reconciliation.
 * 7. Comprehensive Meta Graph API error code & sub-code classification.
 * 
 * Security Invariant:
 * META_APP_SECRET and raw access tokens are strictly server-side and never returned to clients.
 */
class MetaGraphService {
  constructor() {
    this.apiVersion = process.env.META_API_VERSION || 'v20.0';
    this.baseUrl = `https://graph.facebook.com/${this.apiVersion}`;
  }

  getAppId() {
    return process.env.META_APP_ID || '';
  }

  getAppSecret() {
    return process.env.META_APP_SECRET || '';
  }

  getRedirectUri() {
    return process.env.META_REDIRECT_URI || 'https://social-ai-agent-backend.onrender.com/auth/facebook/callback';
  }

  /**
   * Helper for Graph API HTTP GET / POST calls
   */
  async request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;
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
      const errorObj = data.error || {};
      const classified = this.classifyMetaError({
        statusCode: response.status,
        code: errorObj.code,
        subcode: errorObj.error_subcode,
        type: errorObj.type,
        message: errorObj.message || `Graph API HTTP ${response.status}`,
        raw: data
      });
      const error = new Error(classified.message);
      error.metaCode = errorObj.code;
      error.metaSubcode = errorObj.error_subcode;
      error.errorCode = classified.errorCode;
      error.category = classified.category;
      error.isRetryable = classified.isRetryable;
      error.statusCode = response.status;
      error.rawResponse = data;
      throw error;
    }

    return data;
  }

  /**
   * 1. Exchange authorization code for short-lived User Access Token.
   */
  async exchangeCodeForUserToken(code, redirectUri = null) {
    const appId = this.getAppId();
    const appSecret = this.getAppSecret();
    const targetRedirectUri = redirectUri || this.getRedirectUri();

    if (!appId || !appSecret) {
      const err = new Error('Meta App credentials (META_APP_ID / META_APP_SECRET) not configured');
      err.errorCode = 'SERVER_CONFIGURATION_ERROR';
      err.category = 'PERMANENT';
      throw err;
    }

    const endpoint = `/oauth/access_token?client_id=${encodeURIComponent(appId)}&redirect_uri=${encodeURIComponent(targetRedirectUri)}&client_secret=${encodeURIComponent(appSecret)}&code=${encodeURIComponent(code)}`;
    const data = await this.request(endpoint);
    return {
      accessToken: data.access_token,
      tokenType: data.token_type,
      expiresIn: data.expires_in
    };
  }

  /**
   * 2. Exchange short-lived User Token for 60-day Long-Lived User Token.
   */
  async exchangeForLongLivedUserToken(shortLivedToken) {
    const appId = this.getAppId();
    const appSecret = this.getAppSecret();

    if (!appId || !appSecret) {
      const err = new Error('Meta App credentials not configured');
      err.errorCode = 'SERVER_CONFIGURATION_ERROR';
      err.category = 'PERMANENT';
      throw err;
    }

    const endpoint = `/oauth/access_token?grant_type=fb_exchange_token&client_id=${encodeURIComponent(appId)}&client_secret=${encodeURIComponent(appSecret)}&fb_exchange_token=${encodeURIComponent(shortLivedToken)}`;
    const data = await this.request(endpoint);
    return {
      accessToken: data.access_token,
      tokenType: data.token_type,
      expiresIn: data.expires_in || (60 * 24 * 60 * 60) // Default 60 days
    };
  }

  /**
   * 3. Discover Facebook Pages & Linked Instagram Business/Professional Accounts.
   */
  async discoverAccounts(userAccessToken) {
    // 3a. Query /me/accounts for Facebook Pages and linked Instagram Business Accounts
    const endpoint = `/me/accounts?fields=id,name,category,access_token,tasks,instagram_business_account{id,username,name,profile_picture_url}&access_token=${encodeURIComponent(userAccessToken)}`;
    const accountsData = await this.request(endpoint);
    const pages = accountsData.data || [];

    const discoveredFacebookPages = [];
    const discoveredInstagramAccounts = [];

    for (const page of pages) {
      // Analyze tasks for Facebook Page capabilities
      const tasks = page.tasks || [];
      const hasCreateContent = tasks.includes('CREATE_CONTENT') || tasks.includes('MANAGE');
      const hasModerate = tasks.includes('MODERATE') || tasks.includes('MANAGE');
      const hasAnalyze = tasks.includes('ANALYZE') || tasks.includes('ADVERTISE') || tasks.includes('MANAGE');

      const fbCapabilities = [];
      if (hasCreateContent) {
        fbCapabilities.push('CREATE_POST', 'PUBLISH_POST', 'MEDIA_UPLOAD');
      }
      if (hasModerate) {
        fbCapabilities.push('READ_COMMENTS', 'REPLY_COMMENT');
      }
      if (hasAnalyze) {
        fbCapabilities.push('READ_ANALYTICS');
      }
      if (fbCapabilities.length === 0) {
        fbCapabilities.push('READ_ANALYTICS');
      }

      discoveredFacebookPages.push({
        platform: 'FACEBOOK',
        platformUserId: page.id,
        accountName: page.name,
        handle: `@${page.name.toLowerCase().replace(/[^a-z0-9_]/g, '')}`,
        accountType: 'PAGE',
        category: page.category || 'Business / Page',
        accessToken: page.access_token, // Page-specific access token (never expires if derived from long-lived user token)
        tasks: tasks,
        capabilities: Array.from(new Set(fbCapabilities)),
        connectionStatus: 'CONNECTED',
        tokenStatus: 'VALID'
      });

      // 3b. Check linked Instagram Business / Professional Account
      if (page.instagram_business_account && page.instagram_business_account.id) {
        const ig = page.instagram_business_account;
        // Instagram Professional account is supported for publishing via Graph API
        const igCapabilities = [
          'CREATE_POST',
          'PUBLISH_POST',
          'MEDIA_UPLOAD',
          'READ_ANALYTICS',
          'READ_COMMENTS',
          'REPLY_COMMENT',
          'STORY_PUBLISH',
          'REEL_PUBLISH'
        ];

        discoveredInstagramAccounts.push({
          platform: 'INSTAGRAM',
          platformUserId: ig.id,
          accountName: ig.name || ig.username || `${page.name} Instagram`,
          handle: ig.username ? `@${ig.username}` : `@${page.name.toLowerCase().replace(/[^a-z0-9_]/g, '')}_ig`,
          avatarUrl: ig.profile_picture_url || '',
          accountType: 'BUSINESS', // Supported Professional/Business account
          linkedFacebookPageId: page.id,
          accessToken: page.access_token, // Instagram publishing uses the linked Facebook Page token
          capabilities: igCapabilities,
          connectionStatus: 'CONNECTED',
          tokenStatus: 'VALID',
          isProfessional: true
        });
      }
    }

    return {
      pages: discoveredFacebookPages,
      instagramAccounts: discoveredInstagramAccounts
    };
  }

  /**
   * 4. Publish to Facebook Page.
   * Handles text, link, and photo publication.
   */
  async publishFacebookPost({ pageId, pageAccessToken, message, link, mediaUrl }) {
    if (!pageId || !pageAccessToken) {
      const err = new Error('Facebook Page ID and Page Access Token are required for publishing');
      err.errorCode = 'INVALID_PARAMETER';
      err.category = 'PERMANENT';
      throw err;
    }

    // Single photo post
    if (mediaUrl) {
      const photoEndpoint = `/${pageId}/photos`;
      const body = {
        url: mediaUrl,
        caption: message || '',
        access_token: pageAccessToken
      };
      const result = await this.request(photoEndpoint, {
        method: 'POST',
        body
      });

      const externalPostId = result.post_id || result.id;
      return {
        externalPostId,
        externalPostUrl: `https://facebook.com/${pageId}/posts/${externalPostId}`,
        rawResponse: result
      };
    }

    // Text / Link post
    const feedEndpoint = `/${pageId}/feed`;
    const body = {
      message: message || '',
      access_token: pageAccessToken
    };
    if (link) {
      body.link = link;
    }

    const result = await this.request(feedEndpoint, {
      method: 'POST',
      body
    });

    const externalPostId = result.id;
    return {
      externalPostId,
      externalPostUrl: `https://facebook.com/${pageId}/posts/${externalPostId}`,
      rawResponse: result
    };
  }

  /**
   * 5. Publish to Instagram Professional Account.
   * Multi-phase flow:
   * 1) POST /{igUserId}/media (Create media container)
   * 2) GET /{containerId}?fields=status_code (Poll status if needed)
   * 3) POST /{igUserId}/media_publish (Publish container)
   */
  async publishInstagramMedia({ igUserId, accessToken, imageUrl, caption }) {
    if (!igUserId || !accessToken) {
      const err = new Error('Instagram User ID and Access Token are required for publishing');
      err.errorCode = 'INVALID_PARAMETER';
      err.category = 'PERMANENT';
      throw err;
    }

    if (!imageUrl) {
      const err = new Error('Instagram requires a valid public media URL (image or video) for publishing');
      err.errorCode = 'MEDIA_REQUIRED';
      err.category = 'PERMANENT';
      throw err;
    }

    // Step 1: Create Media Container
    const containerEndpoint = `/${igUserId}/media`;
    const containerBody = {
      image_url: imageUrl,
      caption: caption || '',
      access_token: accessToken
    };

    const containerResult = await this.request(containerEndpoint, {
      method: 'POST',
      body: containerBody
    });

    const containerId = containerResult.id;
    if (!containerId) {
      const err = new Error('Instagram container creation failed: No container ID returned');
      err.errorCode = 'CONTAINER_CREATION_FAILED';
      err.category = 'TRANSIENT';
      throw err;
    }

    // Step 2: Check container status (max 5 polls with 2s backoff)
    let containerReady = false;
    let pollAttempts = 0;
    const maxPolls = 5;

    while (!containerReady && pollAttempts < maxPolls) {
      pollAttempts++;
      try {
        const statusResult = await this.request(`/${containerId}?fields=status_code,status&access_token=${encodeURIComponent(accessToken)}`);
        const code = (statusResult.status_code || statusResult.status || '').toUpperCase();

        if (code === 'FINISHED' || code === 'READY' || code === '') {
          containerReady = true;
          break;
        } else if (code === 'ERROR' || code === 'EXPIRED') {
          const err = new Error(`Instagram container processing failed with status ${code}`);
          err.errorCode = 'MEDIA_PROCESSING_FAILED';
          err.category = 'TRANSIENT';
          throw err;
        } else if (code === 'IN_PROGRESS') {
          // Wait 2 seconds before next poll
          await new Promise(r => setTimeout(r, 2000));
        } else {
          containerReady = true;
          break;
        }
      } catch (pollErr) {
        if (pollErr.errorCode === 'MEDIA_PROCESSING_FAILED') throw pollErr;
        // Proceed to publish attempt if status query is unsupported on container
        containerReady = true;
        break;
      }
    }

    // Step 3: Publish Media Container
    const publishEndpoint = `/${igUserId}/media_publish`;
    const publishBody = {
      creation_id: containerId,
      access_token: accessToken
    };

    const publishResult = await this.request(publishEndpoint, {
      method: 'POST',
      body: publishBody
    });

    const externalMediaId = publishResult.id;
    return {
      externalPostId: externalMediaId,
      externalPostUrl: `https://instagram.com/p/${externalMediaId}`,
      rawResponse: publishResult
    };
  }

  /**
   * 6. Reconcile Facebook Post during Ambiguous Network State.
   * Checks recent feed posts to determine if the post was already created.
   */
  async reconcileFacebookPost({ pageId, pageAccessToken, message }) {
    try {
      const endpoint = `/${pageId}/feed?fields=id,message,created_time&limit=10&access_token=${encodeURIComponent(pageAccessToken)}`;
      const result = await this.request(endpoint);
      const posts = result.data || [];

      if (!message || posts.length === 0) {
        return { status: 'NOT_FOUND' };
      }

      const cleanTargetMsg = message.trim().toLowerCase();
      const match = posts.find(p => {
        if (!p.message) return false;
        const cleanMsg = p.message.trim().toLowerCase();
        return cleanMsg === cleanTargetMsg || (cleanTargetMsg.length > 20 && cleanMsg.startsWith(cleanTargetMsg.slice(0, 50)));
      });

      if (match) {
        return {
          status: 'FOUND',
          externalPostId: match.id,
          externalPostUrl: `https://facebook.com/${pageId}/posts/${match.id}`
        };
      }

      return { status: 'NOT_FOUND' };
    } catch (err) {
      if (err.category === 'PERMANENT') throw err;
      return { status: 'INDETERMINATE', error: err.message };
    }
  }

  /**
   * 7. Reconcile Instagram Post during Ambiguous Network State.
   */
  async reconcileInstagramPost({ igUserId, accessToken, caption }) {
    try {
      const endpoint = `/${igUserId}/media?fields=id,caption,timestamp&limit=10&access_token=${encodeURIComponent(accessToken)}`;
      const result = await this.request(endpoint);
      const mediaList = result.data || [];

      if (!caption || mediaList.length === 0) {
        return { status: 'NOT_FOUND' };
      }

      const cleanTargetCaption = caption.trim().toLowerCase();
      const match = mediaList.find(m => {
        if (!m.caption) return false;
        const cleanCap = m.caption.trim().toLowerCase();
        return cleanCap === cleanTargetCaption || (cleanTargetCaption.length > 20 && cleanCap.startsWith(cleanTargetCaption.slice(0, 50)));
      });

      if (match) {
        return {
          status: 'FOUND',
          externalPostId: match.id,
          externalPostUrl: `https://instagram.com/p/${match.id}`
        };
      }

      return { status: 'NOT_FOUND' };
    } catch (err) {
      if (err.category === 'PERMANENT') throw err;
      return { status: 'INDETERMINATE', error: err.message };
    }
  }

  /**
   * 8. Meta Graph API Error Taxonomy Classifier.
   */
  classifyMetaError({ statusCode, code, subcode, type, message }) {
    // 8a. Token Expiration & Revocation
    if (code === 190) {
      if (subcode === 463 || subcode === 467) {
        return {
          errorCode: 'TOKEN_EXPIRED',
          category: 'PERMANENT',
          isRetryable: false,
          message: 'Meta access token has expired. Re-authentication required.'
        };
      }
      if (subcode === 460 || subcode === 458 || subcode === 459) {
        return {
          errorCode: 'TOKEN_REVOKED',
          category: 'PERMANENT',
          isRetryable: false,
          message: 'Meta access token was revoked or password changed. Re-authentication required.'
        };
      }
      return {
        errorCode: 'AUTH_INVALID',
        category: 'PERMANENT',
        isRetryable: false,
        message: message || 'Invalid Meta OAuth authorization.'
      };
    }

    // 8b. Rate Limiting (Codes 4, 17, 32, 613, 80004)
    if (code === 4 || code === 17 || code === 32 || code === 613 || code === 80004) {
      return {
        errorCode: 'RATE_LIMITED',
        category: 'RATE_LIMIT',
        isRetryable: true,
        message: 'Meta Graph API rate limit exceeded. Retry scheduled with backoff.'
      };
    }

    // 8c. Policy Restrictions & Spam Blocks
    if (code === 368 || code === 10) {
      return {
        errorCode: 'POLICY_VIOLATION',
        category: 'PERMANENT',
        isRetryable: false,
        message: `Meta policy or permission restriction: ${message}`
      };
    }

    // 8d. Media & Parameter Issues
    if (code === 100 || code === 2207001 || code === 2207003) {
      if (code === 2207001 || code === 2207003) {
        return {
          errorCode: 'MEDIA_PROCESSING_FAILED',
          category: 'TRANSIENT',
          isRetryable: true,
          message: `Instagram media processing error: ${message}`
        };
      }
      return {
        errorCode: 'INVALID_PARAMETER',
        category: 'PERMANENT',
        isRetryable: false,
        message: `Invalid Graph API parameter: ${message}`
      };
    }

    // 8e. Transient Server Errors (HTTP 500, 502, 503, 504)
    if (statusCode >= 500 && statusCode < 600) {
      return {
        errorCode: 'TRANSIENT_GRAPH_ERROR',
        category: 'TRANSIENT',
        isRetryable: true,
        message: `Meta Graph API server error (${statusCode}): ${message}`
      };
    }

    // 8f. Ambiguous Network Timeout
    if (statusCode === 408 || message.includes('timeout') || message.includes('ETIMEDOUT')) {
      return {
        errorCode: 'AMBIGUOUS_TIMEOUT',
        category: 'AMBIGUOUS',
        isRetryable: true,
        message: 'Network timeout during Meta Graph API call.'
      };
    }

    return {
      errorCode: 'META_API_ERROR',
      category: 'TRANSIENT',
      isRetryable: true,
      message: message || 'Meta Graph API call failed'
    };
  }
}

module.exports = new MetaGraphService();
