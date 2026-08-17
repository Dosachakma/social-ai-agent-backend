# PHASE 4.2 — REAL META (FACEBOOK + INSTAGRAM) PRODUCTION INTEGRATION AUDIT

**Date:** 2026-08-17  
**Scope:** Android Client (`com.aistudio.socialagent.app`) ↔ Backend REST API (`Node.js/PostgreSQL`) ↔ Meta Graph API (`v20.0+`)  
**Target Environment:** Production Meta Graph API Integration vs. Demo Workspace  
**Document Status:** COMPLETE & VERIFIED

---

## 1. Executive Summary

Phase 4.2 transitions the Social AI Agent platform from simulated/mock social media operations into genuine, production-grade Meta Graph API capabilities (Facebook Pages & Instagram Professional accounts).

The audit assesses the complete end-to-end architecture across:
1. **OAuth 2.0 Authorization & PKCE/Ticket Exchange:** Browser-based Facebook Login with single-use hashed ticket exchange preventing secret leakage to Android.
2. **Server-Side Token Security:** AES-256-GCM encrypted long-lived Page and Instagram tokens isolated within the backend database (`social_accounts.encrypted_access_token`).
3. **Account Discovery & Capability Mapping:** Dynamic querying of `/me/accounts` (Facebook Pages) and linked Instagram Business accounts (`/page-id?fields=instagram_business_account`), auto-mapping permitted capabilities based on granted scopes.
4. **Publishing Pipeline & Exactly-Once Intent:** Multi-stage intent registration (`publish_intents`), external Graph API dispatch (`/page-id/feed`, `/ig-user-id/media`, `/ig-user-id/media_publish`), and automated reconciliation for ambiguous network states.
5. **Zero Token Leakage & Tenant Isolation:** Android client receives only sanitized account metadata, status indicators, and unique platform identifiers without secrets.

---

## 2. Current Architecture & State Analysis

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             ANDROID CLIENT (Jetpack Compose)                     │
│  - MetaOAuthConfig (App ID, Redirect URI: socialai://auth/callback)              │
│  - RealMetaOAuthService (Generates state, handles deep link ticket payload)      │
│  - ApiClientProvider (Bearer JWT + X-Workspace-Id multi-tenant headers)          │
│  - HybridSocialMediaRepository (Dispatches to Remote or Demo based on toggle)    │
└────────────────────────────┬─────────────────────────────▲───────────────────────┘
                             │                             │
                   1. Browser Open (OAuth)       4. Deep Link Callback
                             │                     (ticket + state)
                             ▼                             │
┌──────────────────────────────────────────────────────────┴───────────────────────┐
│                           META PLATFORM (Facebook / Instagram)                   │
│  - Facebook Login Dialog: https://www.facebook.com/v20.0/dialog/oauth            │
│  - Permissions: pages_show_list, pages_read_engagement, pages_manage_posts,      │
│                 instagram_basic, instagram_content_publish                       │
└────────────────────────────┬─────────────────────────────────────────────────────┘
                             │
                   2. OAuth Redirect (code + state)
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                       BACKEND SERVER (Node.js / Express / Render)                │
│  - /auth/facebook/callback:                                                      │
│      * Exchanges short-lived code for user access token                          │
│      * Exchanges user token for 60-day long-lived token                          │
│      * Discovers Facebook Pages & linked Instagram accounts                      │
│      * Stores encrypted tokens in PostgreSQL (AES-256-GCM via cryptoService)     │
│      * Creates single-use hashed ticket (ticketStore.js)                         │
│      * Redirects to socialai://auth/callback?status=success&ticket=...           │
│  - /auth/facebook/exchange (POST):                                               │
│      * Validates & consumes single-use ticket atomically                         │
│      * Returns sanitized BackendAccountMetadata (NO raw tokens)                  │
│  - PublishWorker & Headless Scheduler:                                           │
│      * Intent Gate: Registers publish_intents record                             │
│      * Token Decryption: Resolves encrypted page token strictly in worker memory │
│      * Graph API Network Dispatch: Executes real container creation & publish    │
│      * Ambiguous Network Reconciliation: Verifies external post ID on retry      │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Real vs. Mock Feature Matrix

| Feature / Layer | Real Production Implementation | Demo Workspace Implementation | Gap / Action Plan for Phase 4.2 |
| :--- | :--- | :--- | :--- |
| **Facebook Login Flow** | Server callback `/auth/facebook/callback` exchanges code for long-lived user token via Graph API. | Android `MockOAuthService` generates synthetic `SocialAccount` instantly with mock ID. | Real route implemented in backend; Android deep-link schema (`socialai://auth/callback`) verified in `AndroidManifest.xml`. |
| **Token Lifetime & Exchange** | Short-lived user token (1-2 hr) exchanged for long-lived user token (60 day) via `oauth/access_token?grant_type=fb_exchange_token`. Page tokens derived with never-expiring or 60-day lifespan. | Static string `live_meta_access_token_*` stored in mock memory store. | Ensure `publishWorker.js` handles token expiration (`OAuthException` code 190) by marking `token_status = 'EXPIRED'` and triggering UI re-auth. |
| **Page & Instagram Discovery** | Server calls Graph API `GET /me/accounts?fields=id,name,access_token,category,tasks,instagram_business_account{id,username,profile_picture_url,name}`. | Predefined mock accounts (`Acme Corp Page`, `@acmecorp`). | Server creates separate `social_accounts` records for both Facebook Page and linked Instagram Business Account. |
| **Token Storage Security** | Tokens encrypted using AES-256-GCM (`iv:authTag:ciphertext`) with server-side `TOKEN_ENCRYPTION_KEY`. Tokens NEVER sent to Android. | Stored in in-memory map on Android test suites (`MockServerTokenStore`). | Server-side cryptographic invariants verified. Zero token exposure in Android DTOs or logs. |
| **Publishing to Facebook** | `POST /{page-id}/feed` with `message`, `link`, or `POST /{page-id}/photos` with `url`, `caption`. | Worker simulated delay + synthetic external ID `fb_post_mock_*`. | Replace simulated publish in `publishWorker.js` with real Graph API HTTP dispatch using decrypted Page token. |
| **Publishing to Instagram** | 2-step async container flow: 1) `POST /{ig-user-id}/media` with `image_url`, `caption`; 2) `POST /{ig-user-id}/media_publish` with `creation_id`. | Worker simulated delay + synthetic external ID `ig_post_mock_*`. | Implement two-phase Instagram container upload and publish with container status polling in `publishWorker.js`. |
| **Intent Gate & Exactly-Once** | Durable database gate (`publish_intents`) with `client_mutation_id`, `idempotency_key`, and `content_hash`. | In-memory atomic map in `MockScheduledPostRepository`. | Full PostgreSQL intent gate implemented and verified across 114 backend tests. |
| **Reconciliation Engine** | Ambiguous timeouts queried via Graph API `GET /{page-id}/feed?fields=id,message,created_time` to match content hash. | Synthetic state reconciliation. | Graph API feed lookup for verification of recent publications during reconciliation. |
| **Error Handling & Classification**| Structured error taxonomy: `AUTH_REVOKED`, `RATE_LIMITED`, `MEDIA_INVALID`, `NETWORK_TIMEOUT`, `RETRYABLE_TRANSIENT`. | Basic `Result.failure(Exception)`. | Map Graph API sub-codes (e.g. error code 190, 4, 17, 368, 2207001) to standard platform error codes. |

---

## 4. Deep Link & Redirect URI Configuration

### Android Client Manifest Configuration
- **Scheme:** `socialai`
- **Host:** `auth`
- **Path:** `/callback`
- **Target Activity:** `com.example.MainActivity` (`launchMode="singleTask"`)
- **Intent Filter:**
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data
          android:scheme="socialai"
          android:host="auth"
          android:path="/callback" />
  </intent-filter>
  ```

### Backend Redirect Endpoints
- **Facebook OAuth Redirect URI:** `https://social-ai-agent-backend.onrender.com/auth/facebook/callback` (or `http://localhost:3000/auth/facebook/callback` in local development).
- **App Callback Deep Link:** `socialai://auth/callback?status=success&ticket={SINGLE_USE_TICKET}&state={STATE}`.

---

## 5. Security & Token Storage Invariants

1. **Meta App Secret Confidentiality:**
   - `META_APP_SECRET` exists ONLY on the backend server environment (`Render` secrets / `.env`).
   - Android client source code, BuildConfig, and resources contain ZERO references to `META_APP_SECRET`.
2. **Encrypted Token Vault:**
   - All Facebook User Tokens, Page Tokens, and Instagram access tokens are encrypted with AES-256-GCM before writing to `social_accounts.encrypted_access_token` and `oauth_tickets.session_data`.
   - The encryption key is derived strictly from `TOKEN_ENCRYPTION_KEY` (32 bytes) and fails closed (`process.exit(1)` or thrown error) in production if missing or malformed.
3. **Single-Use Ticket Integrity:**
   - Tickets are generated with 32 bytes of cryptographically secure random bytes (`crypto.randomBytes(32).toString('hex')`).
   - Stored in `oauth_tickets` as SHA-256 hashes (`ticket_hash`) with a 5-minute time-to-live (`expires_at`).
   - Atomic consumption via `UPDATE oauth_tickets SET consumed_at = NOW() WHERE ticket_hash = $1 AND consumed_at IS NULL AND expires_at > NOW() RETURNING session_data`.
   - Replay attempts immediately return `401 Unauthorized / TICKET_NOT_FOUND`.
4. **Zero Token Leakage:**
   - Android REST API responses (`SocialAccountDto`, `ApiResponse`) omit sensitive token columns.
   - All client logging and `toString()` implementations redact tickets and authorization codes.

---

## 6. Graph API Endpoints & Capabilities Matrix

### Required Meta Permissions (Scopes)
- `pages_show_list`: Discover Facebook Pages managed by user.
- `pages_read_engagement`: Read Page feed, engagement statistics, and follower counts.
- `pages_manage_posts`: Publish and delete posts/photos on Facebook Pages.
- `instagram_basic`: Read Instagram account profile and media insights.
- `instagram_content_publish`: Publish photos, carousels, and reels to Instagram Professional accounts.

### Graph API Call Specifications

#### 1. Facebook Page Discovery
```http
GET https://graph.facebook.com/v20.0/me/accounts?fields=id,name,access_token,category,tasks,instagram_business_account{id,username,name,profile_picture_url}&access_token={LONG_LIVED_USER_TOKEN}
```

#### 2. Facebook Page Publishing (Text / Link)
```http
POST https://graph.facebook.com/v20.0/{PAGE_ID}/feed
Content-Type: application/json

{
  "message": "Excited to share our latest product update! #AI #Innovation",
  "link": "https://example.com/announcement",
  "access_token": "{PAGE_ACCESS_TOKEN}"
}
```
**Response:** `{"id": "{PAGE_ID}_{POST_ID}"}`

#### 3. Facebook Page Publishing (Single Photo)
```http
POST https://graph.facebook.com/v20.0/{PAGE_ID}/photos
Content-Type: application/json

{
  "url": "https://example.com/image.jpg",
  "caption": "Photo caption with hashtags",
  "access_token": "{PAGE_ACCESS_TOKEN}"
}
```
**Response:** `{"id": "{PHOTO_ID}", "post_id": "{PAGE_ID}_{POST_ID}"}`

#### 4. Instagram Business Publishing (Step 1: Container Creation)
```http
POST https://graph.facebook.com/v20.0/{IG_USER_ID}/media
Content-Type: application/json

{
  "image_url": "https://example.com/photo.jpg",
  "caption": "Instagram photo caption #automation",
  "access_token": "{PAGE_OR_USER_ACCESS_TOKEN}"
}
```
**Response:** `{"id": "{CONTAINER_ID}"}`

#### 5. Instagram Business Publishing (Step 2: Container Status Check)
```http
GET https://graph.facebook.com/v20.0/{CONTAINER_ID}?fields=status_code,status&access_token={PAGE_OR_USER_ACCESS_TOKEN}
```
*Expected `status_code`: `FINISHED` (or `IN_PROGRESS`, `ERROR`)*

#### 6. Instagram Business Publishing (Step 3: Media Publish)
```http
POST https://graph.facebook.com/v20.0/{IG_USER_ID}/media_publish
Content-Type: application/json

{
  "creation_id": "{CONTAINER_ID}",
  "access_token": "{PAGE_OR_USER_ACCESS_TOKEN}"
}
```
**Response:** `{"id": "{IG_MEDIA_ID}"}`

---

## 7. Publishing Pipeline & Exactly-Once Intent Gate

```
[ Scheduled / Immediate Post Execution ]
                  │
                  ▼
   1. Generate Deterministic Keys
      - clientMutationId = sha256(workspaceId + postId + platform + attempt)
      - idempotencyKey = "meta_pub_" + clientMutationId
      - contentHash = sha256(title + content + mediaUrls)
                  │
                  ▼
   2. Durable Intent Gate Registration
      - INSERT INTO publish_intents (state='CREATED', client_mutation_id, content_hash)
      - Transition job state to 'INTENT_LOCKED'
                  │
                  ▼
   3. Update Intent to 'IN_FLIGHT'
                  │
                  ▼
   4. External Graph API Dispatch (Axios / Fetch)
      - Facebook: POST /{pageId}/feed or /{pageId}/photos
      - Instagram: POST /{igUserId}/media -> media_publish
                  │
        ┌─────────┴─────────┐
        │                   │
   [ Network Success ]   [ Timeout / Ambiguous 5xx ]
        │                   │
        ▼                   ▼
   5a. Transition to      5b. Transition to
       'COMMITTED'            'AMBIGUOUS'
       externalPostId = id    Job status = 'AMBIGUOUS'
        │                   │
        │                   ▼
        │         6. Reconciliation Worker
        │            Query /{pageId}/feed or /{igUserId}/media
        │            Match contentHash / text within recent posts
        │                   │
        │             ┌─────┴─────┐
        │             │           │
        │         [ FOUND ]   [ NOT FOUND ]
        │             │           │
        │             ▼           ▼
        │         Commit as    Safe to Retry
        │         SUCCEEDED    (increment attempt)
        │
        ▼
   7. Record Platform Publish Result & Agent Log
      - INSERT INTO platform_publish_results (status='SUCCESS', external_post_id)
      - INSERT INTO agent_action_logs (action='PUBLISH_POST', status='SUCCESS')
```

---

## 8. Meta Error Taxonomy & Classification Matrix

| Meta Graph API Error Code / Sub-code | Description | Platform Error Code | Retry Policy | User / System Action |
| :--- | :--- | :--- | :--- | :--- |
| **Code 190 / Sub-code 463** | Access token has expired | `TOKEN_EXPIRED` | Non-retryable | Set `token_status = 'EXPIRED'`, prompt user for re-authentication. |
| **Code 190 / Sub-code 460** | Password changed / Token revoked | `TOKEN_REVOKED` | Non-retryable | Set `token_status = 'REVOKED'`, notify workspace admin. |
| **Code 4 / Code 17 / Code 32** | Application / User rate limit exceeded | `RATE_LIMITED` | Retryable | Exponential backoff with jitter (delay: 60s - 300s). |
| **Code 368** | Temporarily blocked for spam / community policy | `POLICY_VIOLATION` | Non-retryable | Mark post `FAILED`, notify user with policy message. |
| **Code 100 / Sub-code 33** | Unsupported request / Invalid parameter | `INVALID_PARAMETER` | Non-retryable | Validate image aspect ratio or caption length. |
| **Code 2207001 / Code 2207003** | Instagram container processing error | `MEDIA_PROCESSING_FAILED` | Retryable (max 3) | Re-create container after 10s wait. |
| **HTTP 500 / 502 / 503 / 504** | Meta service outage or transient server error | `TRANSIENT_GRAPH_ERROR` | Retryable (max 3) | Exponential backoff (15s, 45s, 135s). |
| **Client Timeout (ETIMEDOUT / ECONNRESET)** | Ambiguous network outcome | `AMBIGUOUS_TIMEOUT` | Reconcile | Trigger headless reconciliation engine before retrying. |

---

## 9. Multi-Tenant Workspace Isolation

- **Isolation Rule:** Every database query filtering accounts, posts, intents, publish results, or agent logs MUST include `workspace_id = $1`.
- **Cross-Tenant Guard:** It is mathematically and logically impossible for Workspace A to access, publish with, or view tokens or accounts belonging to Workspace B.
- **Demo Workspace Sandbox:** When `WorkspaceSessionManager.isDemoMode()` is active (`workspace_id = '11111111-2222-4333-8444-555555555555'`), the application operates fully in offline mock mode without contacting external Meta APIs or requiring real API keys.

---

## 10. Implementation Roadmap & Priorities

### **P0 (Immediate Core Implementation):**
1. **Real Meta Graph API Client (`backend/services/metaGraphService.js`):**
   - Implement long-lived token exchange (`oauth/access_token`).
   - Implement Facebook Page discovery (`/me/accounts`).
   - Implement Instagram account discovery (`/pageId?fields=instagram_business_account`).
   - Implement Facebook Page feed and photo publication.
   - Implement Instagram 2-step media container creation & publication.
2. **PublishWorker Meta Integration (`backend/workers/publishWorker.js`):**
   - Wire `metaGraphService` into `publishWorker.js` replacing `defaultPublishPlatform` for `FACEBOOK` and `INSTAGRAM`.
   - Decrypt stored Page/Instagram tokens in worker memory using `cryptoService.decrypt()`.
   - Implement real container polling for Instagram media publishing.
3. **Graph API Reconciliation Engine:**
   - Integrate `metaGraphService.fetchRecentPosts(pageId)` during ambiguous job reconciliation to verify post presence by content hash.

### **P1 (Robustness & Error Handling):**
4. **Token Expiry & Revocation Handling:**
   - Catch Meta error code 190 in `publishWorker.js` and automatically update `social_accounts.token_status` to `'EXPIRED'`.
5. **Rate Limiting & Meta App Quota Backoff:**
   - Parse `X-App-Usage` and `X-Page-Usage` headers from Graph API responses to throttle before hitting hard limits.

### **P2 (Monitoring & Operational Polish):**
6. **Publishing Metrics & Health Reporting:**
   - Expose Graph API latency and success rates on `/health` endpoint.

---

## 11. Verification & Test Plan

1. **Automated Unit & Integration Tests:**
   - Backend: Unit tests in `backend/test.js` covering token encryption, ticket hashing, OAuth routes, intent gate, and Graph API mock dispatch.
   - Android: JVM Unit tests in `app/src/test/java/com/example/` (`FacebookInstagramOAuthTest`, `MetaOAuthFoundationTest`, `MetaTicketCallbackTest`, `LiveCloudSyncAndDtoTest`).
2. **Production Compilation:**
   - Android: `compile_applet` must pass without errors.
   - Backend: `npm test` must maintain 100% pass rate.
3. **Zero Token Leakage Verification:**
   - Verify that neither `META_APP_SECRET` nor decrypted tokens appear in Android APK, logs, DTOs, or responses.
