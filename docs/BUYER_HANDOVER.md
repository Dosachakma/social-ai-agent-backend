# Buyer Handover Documentation
## Social AI Agent — Commercial Asset Transfer Package

Welcome to the **Social AI Agent** codebase handover guide. This document provides complete technical, operational, and architectural documentation for due diligence, asset evaluation, and post-acquisition transfer.

---

## 1. Asset Inventory

The acquired repository includes the complete intellectual property, source code, tests, and configuration assets:

### A. Mobile Application (Android)
- **Framework:** Native Android in Kotlin (Android Gradle Plugin 8.5+, Gradle 8.7+).
- **UI Engine:** Jetpack Compose with Material 3 (M3) Design System, supporting dynamic theming and responsive layouts.
- **Architecture:** Clean MVVM (Model-View-ViewModel) with Kotlin Coroutines, StateFlow, and Jetpack Navigation Compose.
- **Local Storage:** Android Room Database with SQLite for offline-first caching and persistence.
- **Networking:** Retrofit 2 + OkHttp 3 with interceptors for JWT Bearer injection and token refresh.
- **Authentication:** Google Credential Manager + custom OAuth callback deep-linking.
- **AI Integration:** Google Gemini AI API provider for multi-platform copywriting, hashtag generation, and brand tone synthesis.

### B. Backend Services & API Gateway (Node.js)
- **Runtime:** Node.js 20 LTS / 22 LTS.
- **Framework:** Express.js REST API with comprehensive security middlewares (Helmet-style headers, CORS, body validation, sliding-window rate limiters).
- **Database Engine:** PostgreSQL 14+ / 16 with durable connection pooling (`pg`), transactions, and schema migration runner.
- **Worker Daemon:** Standalone headless background scheduler and publish worker (`backend/worker.js`).
- **Security Engine:** AES-256-GCM token vault (`cryptoService.js`) and distributed single-use OAuth ticket store.

### C. DevOps & Infrastructure
- **Containerization:** Production Dockerfiles (`Dockerfile`, `backend/Dockerfile`) and multi-container orchestration (`docker-compose.yml`).
- **Database Migrations:** SQL migration files `001_initial_schema.sql` through `006_platform_publish_results.sql`.
- **Demo Seeding:** Idempotent enterprise demo data seeder (`backend/db/seed.js`).
- **Verification Suites:** 111 backend integration tests (`backend/test.js`) + Android unit test suite.

---

## 2. Technology Stack & Version Matrix

| Layer | Component | Version / Specification | Purpose |
| :--- | :--- | :--- | :--- |
| **Mobile** | Kotlin | 2.0.0+ | Modern type-safe Android development |
| **Mobile** | Jetpack Compose | Compose BOM (M3) | Declarative UI framework |
| **Mobile** | Room DB | 2.6.1 | Local on-device SQLite ORM |
| **Mobile** | Retrofit | 2.11.0 | REST API client |
| **Mobile** | Coil | 2.6.0 | Async image rendering & caching |
| **Backend** | Node.js | 20.x LTS / 22.x | Backend runtime |
| **Backend** | Express | 4.19.2 | HTTP routing & REST endpoints |
| **Backend** | pg (node-postgres) | 8.23.0 | High-performance PostgreSQL pool |
| **Backend** | dotenv | 16.4.5 | Environment variable configuration |
| **Backend** | cors | 2.8.5 | Cross-Origin Resource Sharing security |
| **Database** | PostgreSQL | 14.x / 16.x | Multi-tenant relational storage |
| **Containers** | Docker | Compose v2 / 3.8+ | Containerized local & cloud deployments |

---

## 3. Architecture & Core Subsystems

### 3.1 Multi-Tenant Organization & Workspace Boundary
- Every database query and API mutation strictly checks `workspace_id`.
- Users belong to `organizations` and have specific roles in `workspaces` (e.g., `owner`, `admin`, `member`, `reviewer`).
- Cross-workspace queries are impossible due to parameter binding and UUID tenant checks.

### 3.2 AES-256-GCM Cryptographic Token Vault
- Social platform access tokens (Facebook, Instagram, LinkedIn) are encrypted before writing to PostgreSQL.
- Encryption format: `iv (12 bytes hex) : authTag (16 bytes hex) : ciphertext (hex)`.
- Key resolution: Strictly reads `TOKEN_ENCRYPTION_KEY` or `ENCRYPTION_KEY` from server environment variables.
- Zero Token Leakage Guarantee: REST API responses, scheduled jobs, intent gates, and agent action logs scrub all token fields.

### 3.3 Server-Side Headless Scheduler
- Postings with `approval_state = 'APPROVED'` and `scheduled_at <= NOW()` are picked up by `schedulerDispatcher.js`.
- Automatically fans out platform-specific jobs in `scheduled_publish_jobs` table.
- Atomic job leasing using `FOR UPDATE SKIP LOCKED` prevents race conditions across multi-instance worker pools.

### 3.4 Exactly-Once Publish Intent Gate & Reconciliation
- Before initiating any external HTTP request to social networks, an intent record is inserted into `publish_intents` with a unique `client_mutation_id` derived from content hashing.
- If the network call succeeds, the intent is committed and result stored in `platform_publish_results`.
- If an ambiguous network timeout or 502/504 occurs, the worker does NOT blindly retry. Instead, it enters the `reconcileAmbiguousIntent()` flow, querying the social network to verify if the post was already created.

---

## 4. Key Credentials & Security Rotation Checklist

Upon acquiring the repository, the buyer should generate fresh production credentials:

| Secret / Key | Environment Variable | Generation / Setup Command |
| :--- | :--- | :--- |
| **Token Vault Key** | `TOKEN_ENCRYPTION_KEY` | `openssl rand -hex 32` (Must be exactly 32 bytes / 64 hex characters) |
| **JWT Secret** | `JWT_SECRET` | `openssl rand -base64 32` (Minimum 32 characters in production) |
| **Database Password** | `POSTGRES_PASSWORD` | Strong password generated via password manager or cloud IAM |
| **Gemini AI API Key** | `GEMINI_API_KEY` | Create at [Google AI Studio](https://aistudio.google.com/) |
| **Meta App ID & Secret** | `META_APP_ID`, `META_APP_SECRET` | Created in [Meta Developer Portal](https://developers.facebook.com/) |

---

## 5. Transfer of Ownership Checklist

### Step 1: Source Code & Version Control
1. Transfer or mirror the Git repository to your organization's GitHub / GitLab / Bitbucket account.
2. Initialize repository branch protection rules on `main` / `master`.

### Step 2: Cloud Infrastructure & Database
1. Provision a managed PostgreSQL instance (e.g., Supabase, Neon, AWS RDS PostgreSQL, DigitalOcean Managed DB).
2. Set up cloud environment variables matching `.env.example`.
3. Deploy the Node.js API and Worker service (e.g. on Render, Railway, AWS ECS, Fly.io, or DigitalOcean App Platform).
4. Run migrations: `npm run migrate`.
5. (Optional) Run demo seeder: `npm run seed`.

### Step 3: Google AI Studio & Android Publishing
1. Register Android SHA-256 fingerprint in Google Play Console.
2. Configure Android release signing keystore in `app/build.gradle.kts` (or CI/CD pipeline).
3. Set your production `META_BACKEND_URL` and `GEMINI_API_KEY` in production `.env`.
4. Build release APK/AAB: `gradle :app:bundleRelease` or `gradle :app:assembleRelease`.

### Step 4: Meta Developer App (For Live Meta OAuth)
1. In Meta Developer Portal, create or transfer ownership of the Meta App.
2. Add "Facebook Login" and "Instagram Graph API" products.
3. Configure Redirect URI to your production backend URL: `https://your-domain.com/auth/facebook/callback`.
4. Update `META_APP_ID` and `META_APP_SECRET` on your server.
