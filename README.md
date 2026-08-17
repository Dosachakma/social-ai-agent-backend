<div align="center">

# Social AI Agent — Enterprise Autonomous Social Media Platform
### Commercial Asset Package • Production-Ready Android App & Distributed Multi-Tenant Backend

[![Node.js Version](https://img.shields.io/badge/node.js-20%20LTS-brightgreen.svg)](https://nodejs.org/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0+-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Backend Tests](https://img.shields.io/badge/Backend%20Tests-111%2F111%20Passed-success.svg)](./backend/test.js)
[![Android Tests](https://img.shields.io/badge/Android%20Tests-Passing-success.svg)](./app)
[![License](https://img.shields.io/badge/License-Commercial%20Transfer-orange.svg)](#)

</div>

---

## 🚀 Executive Summary & Asset Overview

**Social AI Agent** is a full-stack, enterprise-grade social media copilot and autonomous publishing system. Designed from the ground up for high-margin SaaS commercialization, digital marketing agencies, or enterprise content teams, this turnkey asset pairs a **native Android application (Kotlin + Jetpack Compose M3)** with a **horizontally scalable Node.js/PostgreSQL backend architecture**.

### What You Are Acquiring

| Asset Component | Implementation Details |
| :--- | :--- |
| **Native Android Client** | Modern Jetpack Compose UI, Material 3 theming, Navigation Compose, Room database, Google Credential Manager, interactive calendar scheduler, AI brand copilot, and analytics dashboards. |
| **Multi-Tenant REST API** | Node.js (Express), PostgreSQL schema migrations (001–006), tenant-isolated workspace boundaries, sliding-window rate limiters, distributed single-use OAuth ticket vault, and strict security headers. |
| **Headless Publishing Engine** | Dedicated background daemon, atomic PostgreSQL job leasing, deterministic retry policy with exponential backoff & jitter, and dead-letter queue safety. |
| **Reliability Subsystem** | **Exactly-Once Publish Intent Gate** with cryptographic content hashing (`client_mutation_id`), idempotent replay defense, and automatic ambiguous network failure reconciliation. |
| **Cryptographic Token Vault** | Bank-grade **AES-256-GCM** encryption with unique 96-bit IVs and authentication tags. Zero token leakage boundary across all APIs, jobs, and logs. |
| **Turnkey DevOps & Docker** | Production `Dockerfile`, `docker-compose.yml`, standalone worker entrypoint, and idempotent demo seeder. |
| **Comprehensive Test Suites** | **111 automated backend tests** covering every security invariant + comprehensive Android unit/Robolectric test suite. |

---

## 🏗️ System Architecture

```
                                  ┌──────────────────────────────────────────────┐
                                  │      Android Mobile Client (Compose M3)     │
                                  │  - AI Content Studio & Brand Copilot         │
                                  │  - Multi-Channel Scheduling Calendar         │
                                  │  - Approval Queue & Social Account Hub       │
                                  └──────────────────────┬───────────────────────┘
                                                         │ HTTPS / JWT Bearer
                                                         ▼
                                  ┌──────────────────────────────────────────────┐
                                  │       Social AI Agent REST API Gateway       │
                                  │  - Sliding Window Rate Limiter & Abuse Guard │
                                  │  - Multi-Tenant RBAC & Workspace Isolation   │
                                  │  - Single-Use OAuth Ticket Exchange Vault    │
                                  │  - Health Checks & CRUD Endpoints            │
                                  └──────────────┬───────────────────────────────┘
                                                 │
                  ┌──────────────────────────────┴──────────────────────────────┐
                  │                                                             │
                  ▼                                                             ▼
┌───────────────────────────────────┐                         ┌───────────────────────────────────┐
│     PostgreSQL Relational DB      │                         │  Standalone Headless Worker Pool  │
│  - Organizations & Workspaces     │◄────────────────────────┤  - Lease-Based Scheduler Loop     │
│  - AES-256-GCM Token Vault        │   Atomic Claims &       │  - Exactly-Once Intent Gate       │
│  - Scheduled Jobs & Intents       │   Lease Heartbeats      │  - Ambiguous Network Reconciler   │
│  - Social Posts & Audit Logs      │                         │  - Exponential Backoff & DLQ      │
└───────────────────────────────────┘                         └─────────────────┬─────────────────┘
                                                                                │
                                                                                ▼
                                                              ┌───────────────────────────────────┐
                                                              │ External Networks & Mock Adapters │
                                                              │  - Meta / Facebook Graph API      │
                                                              │  - Instagram Graph API            │
                                                              │  - LinkedIn / Twitter Adapters    │
                                                              └───────────────────────────────────┘
```

---

## ⚡ 30-Second Turnkey Quick Start (Docker)

The fastest way to launch the full PostgreSQL database, backend API, standalone worker daemon, and seeded demo data:

### 1. Launch Services via Docker Compose
```bash
# Clone or unpack repository and navigate to root
cd social-ai-agent

# Launch DB, API server, and background worker daemon in isolated containers
docker compose up -d --build
```

### 2. Verify System Health
```bash
# Check container status
docker compose ps

# Test API health endpoint
curl -s http://localhost:3000/health | jq
```

Expected output:
```json
{
  "status": "healthy",
  "environment": "production",
  "database": {
    "status": "connected",
    "connected": true
  },
  "scheduler": {
    "configured": true
  }
}
```

### 3. Inspect Seeded Demo Data
The Docker initialization automatically runs database migrations and seeds a realistic enterprise demo dataset (`Apex Brand Studio` workspace, connected mock social accounts, draft/scheduled/published posts, and audit logs):

```bash
# Run database seed on-demand anytime (idempotent)
docker compose exec api npm run seed
```

---

## 📱 Android Application Setup (Android Studio)

1. Open **Android Studio** (Koala / Ladybug or newer recommended).
2. Select **Open** and choose this project root directory.
3. Allow Gradle to sync dependencies using the Version Catalog (`gradle/libs.versions.toml`).
4. Ensure your `.env` file contains your Gemini API key:
   ```env
   GEMINI_API_KEY=your_gemini_api_key
   META_BACKEND_URL=http://10.0.2.2:3000
   ```
   *(Note: `10.0.2.2` routes from the Android Emulator to your host machine's `localhost:3000`)*.
5. Run the app on any Android Emulator (API 26+) or physical device.

---

## 🛡️ Enterprise Security & Architectural Invariants

- **Zero Token Leakage:** Access and refresh tokens are encrypted using AES-256-GCM with unique 12-byte initialization vectors and 16-byte authentication tags. Cleartext tokens are NEVER stored, returned in JSON payloads, or written to logs.
- **Fail-Closed Production Guarantees:** In production mode (`NODE_ENV=production`), missing encryption keys, unencrypted token formats, or rate limiter failures immediately fail closed (HTTP 500 / 503) rather than risking insecure fallback.
- **Single-Use OAuth Tickets:** OAuth callback codes are hashed with SHA-256 and stored with 5-minute TTLs. They are atomically burned on first exchange to prevent replay attacks.
- **Tenant Isolation:** Every query and mutation enforces strict `workspace_id` validation. Cross-tenant access attempts return 404/403 with comprehensive security logging.
- **Exactly-Once Publishing:** Before external API dispatch, a durable `publish_intents` gate record is written. Network timeouts or ambiguous 502/504 errors trigger automatic reconciliation before any retry is allowed.

---

## 🧪 Comprehensive Verification & Test Suite

### Running Backend Integration Tests (111/111 Tests)
```bash
cd backend
npm test
```
*Executes all 111 comprehensive tests validating database schema, encryption, rate limiting, distributed workers, exactly-once intent gates, and error classification.*

### Running Android JVM & Unit Tests
```bash
gradle :app:testDebugUnitTest
```
*Executes Android ViewModel, Room persistence, and UI logic verification.*

---

## 💼 Commercial Monetization & Acquisition Opportunities

This codebase provides an immediate foundation for several commercial business models:

1. **SaaS Social Media Management Tool:** Launch a web/mobile SaaS competing with Buffer, Hootsuite, and Later with integrated AI agent automation.
2. **Agency White-Label Platform:** Offer sub-accounts and multi-brand workspaces to social media agencies for managing client campaigns with client approval workflows.
3. **Turnkey Mobile AI App:** Publish to Google Play Store with Google Play In-App Subscriptions ($19–$99/month tiers) for autonomous content generation and auto-scheduling.

---

## 📚 Handover & Operations Documentation

- **[Buyer Handover Guide](docs/BUYER_HANDOVER.md)** — Asset inventory, architecture breakdown, credential rotation, and transfer checklists.
- **[Production Runbook](docs/RUNBOOK.md)** — Deployment procedures, cloud infrastructure sizing, monitoring, backups, and incident playbooks.
- **[Buyer Acceptance Test](docs/BUYER_ACCEPTANCE_TEST.md)** — Step-by-step verification checklist for due diligence and acceptance testing.
