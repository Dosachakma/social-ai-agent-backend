# Phase 4.3 — Four-Platform Production Architecture & Real X/Twitter Integration Audit

**Document Version:** 1.0.0  
**Date:** March 2026  
**Status:** APPROVED & ENFORCED  

---

## 1. Product Scope & Strategic Boundary

The **Social AI Agent** product scope strictly supports **four (4) core social platforms**:

1. **Facebook** (Meta Graph API v20.0+)
2. **Instagram** (Instagram Graph API / Meta Creator & Business APIs)
3. **X (formerly Twitter)** (X API v2 with OAuth 2.0 PKCE)
4. **TikTok** (TikTok Content Posting API v2 / Direct Post & Share APIs)

> **CRITICAL SCOPE ENFORCEMENT:**  
> **LinkedIn is NOT part of the product scope at this stage.**  
> All legacy LinkedIn code, DTOs, OAuth routes, tokens, schemas, UI components, tests, and configurations have been cleanly removed. No LinkedIn code or dependencies shall be reintroduced.

---

## 2. Four-Platform Integration Matrix

| Platform | Integration Level | OAuth / Auth Standard | API Surface & Protocols | Key Production Capabilities | Error & Rate Limit Handling |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Facebook** | **REAL** | Meta OAuth 2.0 (Server-Side Secret + Ticket Exchange) | Meta Graph API v20.0+ (`/me/accounts`, `/{page-id}/feed`, `/{page-id}/insights`) | Page Discovery, Feed Posting, Photo/Link attachment, Page Level Analytics, Graph API Diagnostic Validator | Exponential backoff for Code 32/4/17 (rate limit), Subcode 463/467 (expired tokens), 200/506 (permission/duplicate) |
| **Instagram** | **REAL** | Meta OAuth 2.0 (Page-Linked IG Business/Creator Accounts) | Instagram Graph API (`/{page-id}?fields=instagram_business_account`, `/{ig-user-id}/media`, `/{ig-user-id}/media_publish`) | IG Business Account Discovery, Image/Carousel Publishing, Reels Container Creation & Status Polling, Audience Insights | Container status polling (`IN_PROGRESS`, `FINISHED`, `ERROR`), Rate limit tracking (200 posts/24h per user) |
| **X (Twitter)** | **REAL** | OAuth 2.0 with PKCE (`S256` code challenge + server-side token exchange) | X API v2 (`GET /2/users/me`, `POST /2/tweets`, `GET /2/users/:id/tweets`) | 280-char Tweet Composer, Thread Creation, Real-time X API v2 Publishing, Post ID Reconciliation, Duplicate Tweet Guard | Error taxonomy: 429 Rate Limit (with `x-rate-limit-reset`), 401 Token Expired, 403 Duplicate Content / Forbidden, 500/504 Ambiguous Timeout |
| **TikTok** | **PARTIAL / PREVIEW** | TikTok for Developers OAuth 2.0 (PKCE Direct Post) | TikTok Content Posting API v2 (`/v2/post/publish/video/init/`, `/v2/post/publish/status/fetch/`) | Video & Reel Drafts, Sound/Audio/Caption suggestions, Direct Post API container status polling, 9:16 Video Aspect Ratio validation | Direct Post rate limit monitoring, Video container processing states (`PROCESSING_UPLOAD`, `PUBLISH_COMPLETE`, `FAILED`) |
| **LinkedIn** | **OUT OF SCOPE** | *N/A (Removed)* | *N/A (Removed)* | *N/A (Removed)* | *N/A (Removed)* |

---

## 3. Detailed Architectural Specifications

### 3.1 Facebook Architecture (REAL)
- **Authentication**: Meta OAuth 2.0 Server-Side flow. Android requests authorization via system browser; Meta redirects to `/auth/facebook/callback`; backend exchanges code for User Access Token using `META_APP_SECRET` (strictly protected on server); queries `/me/accounts` to fetch Page tokens; mints a single-use opaque ticket in `TicketStore`; client consumes ticket via `/auth/meta/exchange` to receive account metadata without exposing raw tokens.
- **Publishing Engine**: POST to `/{page-id}/feed` with `message`, `link`, or `attached_media`.
- **Diagnostics**: Real-time verification of App ID, Server Backend URI, and Server-Side Secret protection in UI.

### 3.2 Instagram Architecture (REAL)
- **Authentication**: Unified Meta OAuth flow. Backend traverses `/{page-id}?fields=instagram_business_account` to discover linked professional Instagram accounts.
- **Publishing Engine**: Two-step container publishing flow:
  1. POST `/{ig-user-id}/media` (with `image_url` or `video_url` + `caption`) -> returns `container_id`.
  2. For video/reels, poll `/{container-id}?fields=status_code` until `FINISHED`.
  3. POST `/{ig-user-id}/media_publish` (with `creation_id`) -> returns `id` (Media ID).
- **Creator UX**: Visual Grid preview, hashtag optimizer, story/reel container tracker.

### 3.3 X (Twitter) Architecture (REAL)
- **Authentication**: OAuth 2.0 with PKCE:
  - Client or Backend generates cryptographically secure `code_verifier` (43-128 chars) and computes SHA-256 `code_challenge` (Base64URL-encoded).
  - Scope: `tweet.read`, `tweet.write`, `users.read`, `offline.access`.
  - Authorize URL: `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=...&code_challenge=...&code_challenge_method=S256`.
  - Callback: Backend receives `code` and verifies `state`, exchanges for tokens with Basic Auth / PKCE verifier, queries `GET /2/users/me` for user profile, stores encrypted tokens, and mints ticket.
- **Publishing Engine**: POST `/2/tweets` with `{"text": "..."}`. Returns `id` and `text`.
- **Reconciliation & Idempotency**: Pre-flight duplicate check; post-publish reconciliation query via `GET /2/users/:id/tweets?max_results=5` if ambiguous network timeout occurs.

### 3.4 TikTok Architecture (PARTIAL / PREVIEW)
- **Authentication & Posting**: TikTok Content Posting API v2 Direct Post integration architecture. Video file initialization (`/v2/post/publish/video/init/`), chunked video upload, and status fetching (`/v2/post/publish/status/fetch/`).
- **Creator Tooling**: Aspect ratio validation (9:16 vertical format), audio/sound hashtag recommendation engine, caption length limits (2,200 chars), interactive direct post simulation and test sandbox.

---

## 4. Security & Compliance Mandates
1. **Zero Client Secrets**: No `META_APP_SECRET`, `TWITTER_CLIENT_SECRET`, or private signing keys in Android source or APK build config.
2. **Single-Use Ticket Store**: OAuth callbacks never deliver raw OAuth access tokens or refresh tokens to the Android client. Only opaque single-use exchange tickets (TTL 5 minutes) are emitted.
3. **Database Token Encryption**: Server stores OAuth tokens encrypted using AES-256-GCM with PBKDF2-derived keys (`CRYPTO_SECRET_KEY`).
4. **Strict Tenant Isolation**: All endpoints enforce `workspaceId` checks; IDOR protection across posts, accounts, jobs, and analytics.
5. **No Token Logging**: Redacted `toString()` methods across all client-side and server-side logging layers.
