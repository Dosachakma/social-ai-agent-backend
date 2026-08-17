# PHASE 4.1B — PRODUCTION LIVE DATA & REALITY AUDIT REPORT

**Date:** 2026-08-17  
**Scope:** Android Client (`com.aistudio.socialagent.app`) ↔ Backend REST API (`Node.js/PostgreSQL`)  
**Target Environment:** Production Live Data Layer vs. Demo Workspace

---

## Executive Summary
This audit evaluated the full multi-tier architecture of the Social AI Agent platform to verify production data fidelity, multi-tenant workspace isolation, backend API endpoint coverage, and error-handling resilience.

The architecture uses a Hybrid Repository pattern (`HybridSocialMediaRepository`, `HybridBrandProfileRepository`, `HybridScheduledPostRepository`) governed by `WorkspaceSessionManager.isDemoMode()`. While the baseline REST networking and DTO mappings are solid and passing unit tests, critical gaps exist where ViewModels and Services were initialized with isolated Mock singletons or swallowed network exceptions, leading to stale caches or simulated UI fallbacks in live mode.

---

## A. Feature-by-Feature Live vs. Mock Matrix

| Feature | Live Mode Implementation | Demo / Mock Implementation | Verification Status & Current Gap |
| :--- | :--- | :--- | :--- |
| **Workspace & Session** | `WorkspaceSessionManager` (in-memory + preferences) | Hardcoded `ws_default` / `ws_demo` | **P0 GAP:** Dynamic workspace switching did not reactively invalidate/reload remote caches. |
| **Brand Profile** | `RemoteBrandProfileRepository` (`GET/POST /workspaces/:wsId/brand-profile`) | `MockBrandProfileRepository` | **P0 GAP:** In `RemoteBrandProfileRepository`, async fetch ran only on init; ID checks assumed `"brand_"` prefixes causing save failures on PostgreSQL UUIDs. |
| **Social Accounts** | `RemoteSocialMediaRepository` (`GET/POST/PUT/DELETE /workspaces/:wsId/accounts`) | `MockSocialMediaRepository` (pre-seeded 4 accounts) | **P1 GAP:** Background sync methods caught and swallowed network exceptions without exposing error state to UI. |
| **Meta OAuth Lifecycle** | `RealMetaOAuthService` (`/auth/meta/session`, `/auth/meta/exchange`) | `MockMetaOAuthService` (instant deep link simulate) | **VERIFIED:** Fail-closed production OAuth ticket exchange with HMAC integrity verification. |
| **Content Posts** | `RemoteSocialMediaRepository` (`GET/POST/DELETE /workspaces/:wsId/posts`) | `MockSocialMediaRepository` (6 static posts) | **P1 GAP:** UI lacked pull-to-refresh and explicit live-mode network error banner when backend is unreachable. |
| **Scheduled Posts & Calendar** | `RemoteScheduledPostRepository` (`GET/POST/PUT/DELETE /workspaces/:wsId/posts`) | `MockScheduledPostRepository` | **P0 GAP:** `CalendarViewModel` was instantiating `DefaultSchedulerService` with `MockScheduledPostRepository` instead of the hybrid instance. |
| **Post Publishing & Execution** | `RemoteScheduledPostRepository.saveExecutionResult` (`POST /posts/:postId/publish-results`) | Local memory execution | **P1 GAP:** Execution results were not writing audit log entries to backend `/agent-logs` in live mode. |
| **Analytics Metrics** | `RemoteSocialMediaRepository.getAnalytics` (`GET /workspaces/:wsId/analytics`) | `MockSocialMediaRepository` (static 246K reach) | **P1 GAP:** Dashboard UI fell back to static mock numbers if backend returned 0 values. |
| **Agent Plan & Tools** | `SocialAgentOrchestrator` (Gemini API + registered tools) | `MockAIProvider` | **VERIFIED:** Tool execution routes strictly via `AgentToolRegistry` with permission checks. |
| **Activity & Audit Trail** | `RemoteSocialMediaRepository` (`GET /workspaces/:wsId/agent-logs`) | Static sample activity list in UI | **P0 GAP:** `DashboardScreen` fell back to hardcoded mock list if activities were empty in live mode. |

---

## B. Android → Backend API Endpoint Mapping

| Android Consumer (Retrofit / Service) | HTTP Method & Route | Backend Controller / Handler | Purpose |
| :--- | :--- | :--- | :--- |
| `SocialMediaApiService.getAccounts` | `GET /api/v1/workspaces/{wsId}/accounts` | `app.get('/api/v1/workspaces/:workspaceId/accounts')` | List connected channels |
| `SocialMediaApiService.connectAccount` | `POST /api/v1/workspaces/{wsId}/accounts` | `app.post('/api/v1/workspaces/:workspaceId/accounts')` | Connect or save account |
| `SocialMediaApiService.updateAccount` | `PUT /api/v1/workspaces/{wsId}/accounts/{id}` | `app.put('/api/v1/workspaces/:workspaceId/accounts/:id')` | Update token status/connection |
| `SocialMediaApiService.deleteAccount` | `DELETE /api/v1/workspaces/{wsId}/accounts/{id}` | `app.delete('/api/v1/workspaces/:workspaceId/accounts/:id')` | Disconnect and revoke token |
| `SocialMediaApiService.getPosts` | `GET /api/v1/workspaces/{wsId}/posts` | `app.get('/api/v1/workspaces/:workspaceId/posts')` | Fetch all posts |
| `SocialMediaApiService.getScheduledPosts`| `GET /api/v1/workspaces/{wsId}/posts/scheduled` | `app.get('/api/v1/workspaces/:workspaceId/posts/scheduled')`| Fetch scheduled/queue |
| `SocialMediaApiService.getPostById` | `GET /api/v1/workspaces/{wsId}/posts/{id}` | `app.get('/api/v1/workspaces/:workspaceId/posts/:id')` | Single post lookup |
| `SocialMediaApiService.createPost` | `POST /api/v1/workspaces/{wsId}/posts` | `app.post('/api/v1/workspaces/:workspaceId/posts')` | Create draft or scheduled post |
| `SocialMediaApiService.updatePost` | `PUT /api/v1/workspaces/{wsId}/posts/{id}` | `app.put('/api/v1/workspaces/:workspaceId/posts/:id')` | Update content or status |
| `SocialMediaApiService.deletePost` | `DELETE /api/v1/workspaces/{wsId}/posts/{id}` | `app.delete('/api/v1/workspaces/:workspaceId/posts/:id')` | Remove post |
| `SocialMediaApiService.savePublishResult`| `POST /api/v1/workspaces/{wsId}/posts/{id}/publish-results` | `app.post('/api/v1/workspaces/:workspaceId/posts/:postId/publish-results')` | Record platform result |
| `SocialMediaApiService.getBrandProfile` | `GET /api/v1/workspaces/{wsId}/brand-profile` | `app.get('/api/v1/workspaces/:workspaceId/brand-profile')` | Retrieve brand guidelines |
| `SocialMediaApiService.createOrUpdateBrandProfile` | `POST /api/v1/workspaces/{wsId}/brand-profile` | `app.post('/api/v1/workspaces/:workspaceId/brand-profile')` | Upsert brand guidelines |
| `SocialMediaApiService.getAnalytics` | `GET /api/v1/workspaces/{wsId}/analytics` | `app.get('/api/v1/workspaces/:workspaceId/analytics')` | Aggregated metrics |
| `SocialMediaApiService.getAgentLogs` | `GET /api/v1/workspaces/{wsId}/agent-logs` | `app.get('/api/v1/workspaces/:workspaceId/agent-logs')` | Read audit trail |
| `SocialMediaApiService.createAgentLog` | `POST /api/v1/workspaces/{wsId}/agent-logs` | `app.post('/api/v1/workspaces/:workspaceId/agent-logs')` | Record audit entry |
| `SocialMediaApiService.createMetaOAuthSession` | `POST /api/v1/auth/meta/session` | `app.post('/api/v1/auth/meta/session')` | Start Meta OAuth session |
| `SocialMediaApiService.exchangeMetaOAuthTicket` | `POST /api/v1/auth/meta/exchange` | `app.post('/api/v1/auth/meta/exchange')` | Exchange ticket for account |

---

## C. Backend Endpoint → Android Consumer Mapping

All 18 REST endpoints exposed by `backend/server.js` are declared in `SocialMediaApiService.kt`.
Unconsumed routes before this audit:
- `POST /api/v1/workspaces/:workspaceId/agent-logs` was declared in Retrofit but never invoked by `DefaultSchedulerService` or `AIService` when performing actions in live mode.
- `GET /api/v1/workspaces/:workspaceId/posts/:postId/jobs` (Phase 3.4 Headless Job inspection) was not yet exposed on the Android UI.

---

## D. Missing Integrations & Gaps

1. **SchedulerService Repository Binding:** `CalendarViewModel` was not injecting the shared `HybridScheduledPostRepository` and `HybridSocialMediaRepository` into `DefaultSchedulerService`, isolating calendar actions into a mock instance.
2. **Agent Action Audit Log Persistence:** Execution of scheduler actions and agent actions did not call `POST /workspaces/:wsId/agent-logs` in live mode.
3. **Workspace Invalidation & Dynamic Re-fetch:** When the user switches workspaces or changes the backend URL in Settings, repositories retained old cached data until manually restarted.
4. **Silent Error Swallowing in Background Refreshes:** `RemoteSocialMediaRepository` caught all exceptions in `refresh*Internal()` and did not propagate error state to UI flows.

---

## E. Fake / Simulated Production Paths

1. **Dashboard Activity Fallback:** `DashboardScreen` contained a static 4-item hardcoded list that rendered whenever `activities.isEmpty()`. In live mode, an empty list must display the real empty state with a "Generate with AI Copilot" CTA instead of fake Facebook/Instagram logs.
2. **KPI Metric Zero Guard:** `DashboardKpiGrid` substituted `4 Active`, `3 Posts`, `10 Delivered`, `246K Reach` if actual live values were 0. Live mode must display actual values (0 when empty) with clear onboarding prompts.
3. **Brand Profile ID Fallback:** `RemoteBrandProfileRepository` was checking `existingId.startsWith("brand_")` to determine if a brand was mock or real, which rejected valid custom/PostgreSQL IDs.

---

## F. Data Synchronization & Stale State Risks

- **Risk:** Rapid consecutive post creation or approval actions in Calendar could cause optimistic UI state to be overwritten if an asynchronous background refresh returned older database state.
- **Remediation:** Implement local state merging and cache-first update with write-through consistency.

---

## G. Offline / Error Recovery Strategy

- **Live Mode Guarantee:** Live mode must never silently fall back to Mock data.
- **Error Propagation:** Expose `errorMessage` and `syncStatus` in UI states. When backend is down (e.g., `ConnectException` or `503`), display user-visible retry affordance.

---

## H. Authentication & Session Security

- **Multi-Tenant Headers:** All requests automatically include `Authorization: Bearer <token>` and `X-Workspace-Id: <wsId>` via `ApiClientProvider` interceptor.
- **Zero Token Leakage:** Server secrets, access tokens, and OAuth client secrets are never transmitted to or stored on Android. Only sanitized `SocialAccountDto` models are received.

---

## I. Implementation Priorities (P0 / P1 / P2)

### **P0 (Immediate Core Reality Blockers):**
1. **Fix `CalendarViewModel` & `DefaultSchedulerService` Wiring:** Pass `HybridScheduledPostRepository` and `HybridSocialMediaRepository` into `DefaultSchedulerService`.
2. **Fix `RemoteBrandProfileRepository`:** Remove hardcoded ID prefix checks; implement reactive workspace-based profile fetching and persistence.
3. **Fix Live Dashboard UI Fallbacks:** Remove simulated fallback data in `DashboardScreen` so live mode displays real workspace state.
4. **Implement Live Agent Log Persistence:** Connect `DefaultSchedulerService` and repositories to `POST /api/v1/workspaces/{workspaceId}/agent-logs`.

### **P1 (High-Value Robustness):**
5. **Implement Reactive Workspace Refresh:** Allow `Remote*Repository` caches to automatically clear and reload on workspace change.
6. **Propagate Live Sync Errors to UI:** Surface backend errors and offline states in `DashboardUiState`, `CalendarUiState`, `BrandProfileUiState`, and `SettingsUiState`.
7. **Complete Social Accounts Live State Transitions:** Handle token expiration and reauth status updates via live API.

### **P2 (Polish & Extended Capabilities):**
8. Expose Headless Job Status inspection for enterprise queue transparency.
