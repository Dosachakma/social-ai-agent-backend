# Phase 4.3 Discovery Audit: X/Twitter & LinkedIn Production Integration

**Audit Date:** August 2026  
**Audited Subsystems:** Backend (Node.js/Express/PostgreSQL), Android Client (Kotlin/Compose/Retrofit), Token Vault, Scheduler/Publish Worker  
**Target Goal:** Production-grade real API integrations for X/Twitter (API v2) and LinkedIn (OAuth 2.0 / REST API / UGC Posts) with zero raw token exposure to Android clients, exactly-once publishing intent gating, tenant isolation, and full preservation of Demo Workspace.

---

## 1. Subsystem Reality & Capability Matrix

| Component / Subsystem | Current State | Target State (Phase 4.3) | Gap Analysis & Implementation Plan |
| :--- | :--- | :--- | :--- |
| **X/Twitter OAuth 2.0** | **MOCK / SIMULATED** | **REAL (Production PKCE Flow)** | Currently simulated in Android `MockOAuthService`. Needs server-side PKCE OAuth 2.0 (`/i/oauth2/authorize`, `/2/oauth2/token`) with PKCE challenge/verifier management, state verification, and single-use ticket exchange via `TicketStore`. |
| **X/Twitter Account Discovery** | **MOCK / SIMULATED** | **REAL (API v2 `/2/users/me`)** | Currently returns mock handles (`@connected_twitter`). Needs real call to `GET https://api.twitter.com/2/users/me` with `user.fields=profile_image_url,description,public_metrics` to retrieve real user ID, name, username, profile photo, and metrics. |
| **X/Twitter Post Publishing** | **SIMULATED** | **REAL (`POST https://api.twitter.com/2/tweets`)** | Worker simulates publishing without invoking Twitter API. Needs real API v2 tweet creation endpoint integration with JSON payload `{"text": "..."}`, handling idempotency, rate limiting, and tweet character constraints (280 chars / premium). |
| **X/Twitter Error Classification** | **GENERIC** | **PRODUCTION ERROR HIERARCHY** | Needs strict classification: `AUTH_REVOKED` (401), `RATE_LIMITED` (429 with retry-after header parsing), `DUPLICATE_CONTENT` (403), `SUSPENDED_ACCOUNT` (403), `NETWORK_TIMEOUT` (ambiguous reconciliation). |
| **LinkedIn OAuth 2.0** | **MOCK / SIMULATED** | **REAL (OAuth 2.0 3-Legged Flow)** | Currently simulated in Android. Needs server-side OAuth flow (`/oauth/v2/authorization`, `/oauth/v2/accessToken`) with configurable scopes (`openid`, `profile`, `email`, `w_member_social`, `w_organization_social`, `rw_organization_admin`). |
| **LinkedIn Account & Org Page Discovery** | **MOCK / SIMULATED** | **REAL (`/v2/userinfo` & `/v2/organizationalEntityAcls`)** | Needs discovery of both Personal Profile (`urn:li:person:...`) and Company/Organization Pages (`urn:li:organization:...`) where the user has `ADMINISTRATOR` or `DIRECT_SPONSORED_CONTENT_POSTER` roles. |
| **LinkedIn Post Publishing** | **SIMULATED** | **REAL (`/rest/posts` & `/v2/ugcPosts`)** | Needs production-grade post publishing using LinkedIn REST API (`POST https://api.linkedin.com/rest/posts` with `LinkedIn-Version: 202401` and fallback to `/v2/ugcPosts`) supporting author URN resolution and structured commentary text. |
| **LinkedIn Error Classification** | **GENERIC** | **PRODUCTION ERROR HIERARCHY** | Needs strict handling: `EXPIRED_TOKEN` (401), `INSUFFICIENT_SCOPE` (403), `RATE_LIMITED` (429), `ORG_ACCESS_REVOKED` (403), `DUPLICATE_POST` (422). |
| **Token Vault & Tenant Isolation** | **REAL (AES-256-GCM)** | **REAL (Extended for X & LinkedIn)** | Postgres `social_accounts` table and `cryptoService` already securely encrypts Meta tokens. Needs extension to handle X/Twitter refresh tokens & LinkedIn member tokens per `workspaceId`. |
| **Publishing Intent & Idempotency** | **REAL** | **REAL (Multi-Platform Routing)** | `PublishWorker` and `PublishIntentService` guarantee exactly-once execution. Extend dispatch router in `publishWorker.js` to route `TWITTER` and `LINKEDIN` intents to their respective production service callers. |
| **Android Security & Token Non-Leakage** | **REAL (Sanitized Metadata Only)** | **REAL (Sanitized Metadata Only)** | Android client communicates strictly via single-use `ticket` exchange and stores zero tokens or secrets. Extend `OAuthProvider.kt`, `RealOAuthService`, and diagnostics. |
| **Android Capability Mapping** | **PARTIAL (Meta-focused)** | **REAL (Platform-Specific Capabilities)** | Extend dynamic capability badges for X (tweets, analytics, media) and LinkedIn (personal posts, org page posts, analytics, comment reads) with visual UI feedback. |
| **Android UX & Diagnostics** | **PARTIAL** | **FULL PRODUCTION UX** | Add diagnostics card and connection status displays for X/Twitter and LinkedIn in `AccountsScreen.kt`, showing `Connected`, `Re-authentication Required`, `Token Expiring`, and live configuration readiness. |
| **Demo Workspace Preservation** | **REAL** | **REAL (100% Preserved)** | `isDemoData` flags and mock service fallback when `ExecutionEnvironment.MOCK` is active must remain 100% functional and untouched. |

---

## 2. Architecture & Design Blueprint

### 2.1 X/Twitter Integration Flow (OAuth 2.0 PKCE + API v2)
1. **Initiation:** User selects "Connect X / Twitter" in Android (Live Mode).
2. **Session Creation:** Android requests session from Backend `GET /api/v1/auth/twitter?workspaceId=...` or opens Browser to `/auth/twitter?workspaceId=...`.
3. **PKCE & State:** Backend generates `code_verifier` (43-128 chars), SHA-256 `code_challenge`, and unique `state`. Stores in `pkce_sessions` / session cache.
4. **Redirect:** User approves in Twitter OAuth 2.0 dialog (`scope=tweet.read tweet.write users.read offline.access`).
5. **Callback:** Twitter redirects to `GET /auth/twitter/callback?code=...&state=...`.
6. **Token Exchange:** Backend exchanges `code` + `code_verifier` at `https://api.twitter.com/2/oauth2/token` with HTTP Basic Auth (`client_id:client_secret`).
7. **Discovery:** Backend calls `GET https://api.twitter.com/2/users/me?user.fields=profile_image_url,description,public_metrics`.
8. **Vaulting:** Encrypted tokens saved in PostgreSQL `social_accounts` under active `workspaceId`.
9. **Ticket Generation:** Single-use ticket created in `TicketStore` containing sanitized profile metadata.
10. **Deep Link:** Browser redirects to `socialai://auth/callback?status=success&ticket=...&state=...`.
11. **Android Ticket Exchange:** Android calls `POST /api/v1/auth/twitter/exchange` with ticket. Receives sanitized `SocialAccountDto` with zero secrets.

### 2.2 LinkedIn Integration Flow (OAuth 2.0 + REST/UGC API)
1. **Initiation:** User selects "Connect LinkedIn" in Android (Live Mode).
2. **Session Creation:** Backend opens `/auth/linkedin?workspaceId=...`.
3. **State Generation:** Backend creates secure CSRF `state` and redirects to `https://www.linkedin.com/oauth/v2/authorization`.
4. **Callback:** LinkedIn redirects to `GET /auth/linkedin/callback?code=...&state=...`.
5. **Token Exchange:** Backend calls `POST https://www.linkedin.com/oauth/v2/accessToken`.
6. **Discovery:**
   - Calls `GET https://api.linkedin.com/v2/userinfo` to obtain Person URN (`urn:li:person:{sub}`).
   - Calls `GET https://api.linkedin.com/v2/organizationalEntityAcls?q=roleAssignee` to discover Organization Pages (`urn:li:organization:{id}`).
7. **Vaulting:** Encrypted tokens saved in PostgreSQL `social_accounts`.
8. **Ticket Generation:** Single-use ticket created in `TicketStore`.
9. **Deep Link:** Redirects to `socialai://auth/callback?status=success&ticket=...&state=...`.
10. **Android Ticket Exchange:** Android calls `POST /api/v1/auth/linkedin/exchange` to receive sanitized metadata.

### 2.3 Publishing Architecture
- **X/Twitter Publishing (`twitterService.js`):**
  - Uses `POST https://api.twitter.com/2/tweets`.
  - Body: `{"text": "<content>"}`.
  - Automatically handles refresh token rotation if access token is expired (`401`).
- **LinkedIn Publishing (`linkedInService.js`):**
  - Primary: `POST https://api.linkedin.com/rest/posts` with header `LinkedIn-Version: 202401`.
  - Secondary/Fallback: `POST https://api.linkedin.com/v2/ugcPosts`.
  - Body configured for member `urn:li:person:...` or organization `urn:li:organization:...`.
- **Worker Pipeline (`publishWorker.js`):**
  - Multi-platform dispatching: routes `FACEBOOK`, `INSTAGRAM`, `TWITTER`, `LINKEDIN` to real service clients when `isDemoData === false`.
  - Idempotency protection with `PublishIntentService`.

---

## 3. Implementation Checklist
- [x] Step 1: Discovery Audit document completed.
- [ ] Step 2: X/Twitter real OAuth service with PKCE (`twitterOAuthService.js` / `twitterGraphService.js`).
- [ ] Step 3: X/Twitter user profile discovery (`/2/users/me`).
- [ ] Step 4: X/Twitter real publishing (`POST /2/tweets`) & token refresh.
- [ ] Step 5: X/Twitter error handling and rate limit classification.
- [ ] Step 6: LinkedIn real OAuth service (`linkedInOAuthService.js` / `linkedInGraphService.js`).
- [ ] Step 7: LinkedIn profile & organization discovery (`/v2/userinfo` + `/v2/organizationalEntityAcls`).
- [ ] Step 8: LinkedIn real post publishing (`/rest/posts` / `/v2/ugcPosts`).
- [ ] Step 9: Backend route registration & TicketStore integration for X & LinkedIn.
- [ ] Step 10: Publish worker dispatching for X and LinkedIn.
- [ ] Step 11: Android DTOs, Retrofit endpoints, configuration models, and UX diagnostics.
- [ ] Step 12: Comprehensive automated test suites (Backend + Android).
- [ ] Step 13: Full regression verification (`npm test`, Android unit tests, compilation).
- [ ] Step 14: Final Reality Audit document (`docs/PHASE_4_3_X_LINKEDIN_PRODUCTION_INTEGRATION.md`).
