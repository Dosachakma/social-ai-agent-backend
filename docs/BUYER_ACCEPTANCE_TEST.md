# Buyer Acceptance Test (BAT) Checklist
## Social AI Agent — Commercial Quality Assurance & Verification Protocol

This document provides a comprehensive, step-by-step verification protocol for prospective buyers or technical auditors evaluating the **Social AI Agent** codebase for acquisition.

---

## 📋 Verification Matrix Summary

| Test Phase | Verification Objective | Passing Criteria | Status |
| :--- | :--- | :--- | :--- |
| **BAT-01** | Backend Automated Integration Test Suite | 111 / 111 tests pass with zero errors | ✅ PASSED |
| **BAT-02** | Android Mobile Client Build & JVM Tests | Gradle builds successfully and all unit tests pass | ✅ PASSED |
| **BAT-03** | Docker Compose 1-Click Launch | DB, API, and Worker containers start and become healthy | ✅ PASSED |
| **BAT-04** | Idempotent Demo Seeder | Populates realistic enterprise dataset without collisions | ✅ PASSED |
| **BAT-05** | Standalone Background Worker Process | Runs as isolated daemon with clean signal shutdown | ✅ PASSED |
| **BAT-06** | AES-256-GCM Token Vault & Zero Leakage | Secrets encrypted, tamper-detected, and never leaked | ✅ PASSED |
| **BAT-07** | Exactly-Once Publish Intent Gate | Prevents duplicate publishing under ambiguous network drops | ✅ PASSED |

---

## 🛠️ Step-by-Step BAT Execution Guide

### Test BAT-01: Automated Backend Test Suite (111 Tests)
Run the complete backend integration test suite:
```bash
cd backend
npm test
```
**Acceptance Criteria:**
- Console prints `ALL PHASE 1, 2, 2.5, 3.1, 3.2, 3.3A, 3.3B, 3.4 & 3.5 TESTS PASSED (111/111)!`
- Zero unhandled exceptions or token leakages reported.

---

### Test BAT-02: Android Mobile Client Compilation & Unit Tests
Run the Android test suite using Gradle:
```bash
gradle :app:testDebugUnitTest
```
**Acceptance Criteria:**
- Build succeeds with `BUILD SUCCESSFUL`.
- Room database entities, ViewModels, and UI state models pass all assertions.

---

### Test BAT-03: Docker Containerization & Healthcheck
Launch the multi-container environment via Docker Compose:
```bash
docker compose up -d --build
docker compose ps
```
Verify the API health endpoint:
```bash
curl -i http://localhost:3000/health
```
**Acceptance Criteria:**
- HTTP status `200 OK`.
- Response JSON includes `"status": "healthy"`, `"database": {"status": "connected"}`, and `"scheduler": {"configured": true}`.

---

### Test BAT-04: Idempotent Demo Data Seeder
Execute the demo seeder inside the backend container or locally:
```bash
npm run seed
```
Run it a second time to verify idempotency:
```bash
npm run seed
```
**Acceptance Criteria:**
- Both executions succeed without duplicate key errors (`23505`) or schema corruption.
- Seeded workspace `Apex Growth Workspace` and brand profile `NovaFlow AI` are successfully written/updated.

---

### Test BAT-05: Standalone Background Worker Daemon
Execute the standalone worker process:
```bash
node backend/worker.js
```
Send `SIGINT` (Ctrl+C) to test graceful shutdown.

**Acceptance Criteria:**
- Console displays:
  ```
  --- Social AI Agent Standalone Background Worker Daemon ---
  ✓ Headless Scheduler Dispatcher active
  ✓ Headless Publish Worker active
  ```
- On interrupt, logs clean shutdown and closes database pool connections.

---

### Test BAT-06: Cryptographic Token Vault & Zero Leakage Verification
Verify that encrypted access tokens cannot be decoded without the encryption key:
- Tamper with an auth tag or ciphertext byte.
- Call `cryptoService.decrypt(tamperedString)`.
**Acceptance Criteria:**
- Decryption immediately throws `DECRYPTION_FAILED` (auth tag verification fails).
- Serialized responses to `GET /posts/:postId/jobs` and `GET /brand-profiles` omit all tokens.

---

### Test BAT-07: Exactly-Once Publishing & Intent Gate Safety
Verify that when external publishing returns an ambiguous network outcome (e.g. timeout or 504):
- Intent gate marks state `AMBIGUOUS`.
- Reconciliation routine verifies external platform state before attempting any retry.
- Idempotency key and content hash prevent duplicate posts on social feeds.

**Acceptance Criteria:**
- Verified by Test #96 through #108 in `backend/test.js`.
- Zero duplicate external posts are dispatched.

---

## 🎯 Buyer Acceptance Sign-Off

Upon completion of the tests above, the software asset is verified as:
1. **Fully Functional:** Native Android client + Node.js backend + PostgreSQL storage operating reliably.
2. **Turnkey & Reproducible:** Launchable in < 60 seconds via Docker Compose.
3. **Enterprise Secure:** Cryptographic token vault, JWT authentication, and tenant isolation fully intact.
4. **Commercially Ready:** Ready for deployment, re-branding, or integration into existing SaaS portfolios.
