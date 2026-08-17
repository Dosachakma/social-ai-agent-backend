# Production Deployment & Operations Runbook
## Social AI Agent — Enterprise Infrastructure Guide

This runbook describes standard operating procedures for deploying, maintaining, monitoring, and troubleshooting the Social AI Agent full-stack platform in production environments.

---

## 1. Production Architecture & Sizing Guidelines

The system consists of three distinct deployment components:
1. **PostgreSQL Relational Database:** Persistent store for organizations, workspaces, accounts, posts, jobs, and audit logs.
2. **Backend API Service:** Stateless Express HTTP server handling REST traffic, authentication, and client operations.
3. **Background Worker Daemon:** Long-running Node.js process executing scheduling dispatch and platform publishing.

### Capacity & Resource Sizing Matrix

| User Base Tier | Daily Scheduled Posts | Database Specifications | API Server Instances | Background Workers | Recommended Hosting Provider |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Starter (1–1,000 Users)** | < 5,000 posts/day | 1 vCPU, 1 GB RAM (e.g. Supabase Free/Pro, Neon, RDS db.t4g.micro) | 1x Container (0.5 vCPU, 512MB RAM) | 1x Container (0.5 vCPU, 512MB RAM) | Render / Railway / Fly.io ($15–$30/mo) |
| **Growth (1K–25K Users)** | 5,000–100,000 posts/day | 2 vCPU, 4 GB RAM (RDS db.t4g.small / Managed Postgres) | 2–4x Containers (1 vCPU, 1GB RAM) with load balancer | 2–4x Worker Containers (1 vCPU, 1GB RAM) | AWS ECS / DigitalOcean / Render ($75–$250/mo) |
| **Enterprise (25K+ Users)** | > 100,000 posts/day | 4 vCPU, 16 GB RAM + Read Replica | Auto-scaled Container Cluster (Fargate / K8s) | Auto-scaled Worker Pool (Fargate / K8s) | AWS / GCP / Azure ($300–$1,000+/mo) |

---

## 2. Production Deployment Procedures

### Option A: 1-Click Docker Compose Deployment
```bash
# 1. Clone repository to production host
git clone <your-repo-url> /opt/social-ai-agent
cd /opt/social-ai-agent

# 2. Configure production environment variables
cp .env.example .env
nano .env

# Mandatory: Ensure NODE_ENV=production, strong JWT_SECRET, and 32-byte TOKEN_ENCRYPTION_KEY

# 3. Launch with automatic restart policy
docker compose up -d --build

# 4. Verify healthy state
curl -s http://localhost:3000/health | jq
```

### Option B: Cloud PaaS Deployment (Render / Railway / Fly.io)
1. **Provision Managed PostgreSQL Database:**
   - Copy the provided `DATABASE_URL` (with SSL enabled).
2. **Deploy API Service:**
   - Root Directory: `backend`
   - Build Command: `npm install --omit=dev`
   - Start Command: `npm run migrate && npm start`
   - Set environment variables from `.env.example` (`NODE_ENV=production`, `DISABLE_SERVER_WORKERS=true`, `DATABASE_URL`, `TOKEN_ENCRYPTION_KEY`, `JWT_SECRET`).
3. **Deploy Worker Service (Background Worker):**
   - Root Directory: `backend`
   - Build Command: `npm install --omit=dev`
   - Start Command: `npm run worker`
   - Set environment variables (`NODE_ENV=production`, `DATABASE_URL`, `TOKEN_ENCRYPTION_KEY`).

---

## 3. Database Migration & Rollback Protocol

Database migrations are located in `backend/db/migrations/` and use a transactional migration runner (`backend/db/migrate.js`).

### Executing Migrations
```bash
# From within the backend directory or container:
npm run migrate
```
*The runner checks the `schema_migrations` table and applies unapplied SQL files in strict alphabetical order within an atomic transaction.*

### Safe Rollback Strategy
If a migration fails, the transaction automatically rolls back cleanly without leaving partial tables. To revert a migration in production:
1. Write a new reversing migration file (e.g. `007_revert_feature.sql`).
2. Run `npm run migrate`.

---

## 4. Monitoring, Health Checks & Observability

### 4.1 Health Check API Endpoint
Endpoint: `GET /health`
```json
{
  "status": "healthy",
  "environment": "production",
  "uptime": 1284.52,
  "timestamp": "2026-08-16T15:30:00.000Z",
  "database": {
    "status": "connected",
    "connected": true,
    "latencyMs": 4,
    "serverTime": "2026-08-16 15:30:00.123456+00",
    "dbVersion": "PostgreSQL 16.2"
  },
  "scheduler": {
    "configured": true
  }
}
```

### 4.2 Key Operational Metrics to Monitor
- **HTTP 5xx Error Rate:** Should remain < 0.1% under normal operations.
- **Database Latency:** Normal queries < 15ms. Alert if latency > 100ms.
- **Worker Queue Depth:** Query uncompleted jobs:
  ```sql
  SELECT status, COUNT(*) FROM scheduled_publish_jobs GROUP BY status;
  ```
- **Dead-Letter Queue (DLQ):** Check for failed or unresolvable jobs:
  ```sql
  SELECT * FROM scheduled_publish_jobs WHERE status = 'DEAD_LETTER' OR status = 'FAILED';
  ```

---

## 5. Backup & Disaster Recovery Procedures

### Automated Database Backups
```bash
# Automated daily backup script using pg_dump
pg_dump -U postgres -d social_agent -F c -b -v -f "/backups/social_agent_$(date +%Y%m%d_%H%M%S).dump"
```

### Restoring from Backup
```bash
pg_restore -U postgres -d social_agent -v -c "/backups/social_agent_20260816_120000.dump"
```

---

## 6. Incident Response & Troubleshooting Playbooks

### Scenario A: Worker Lease Expiration / Stuck Jobs
- **Symptom:** A job is marked `IN_FLIGHT` for more than 5 minutes due to an unexpected worker container crash.
- **Automatic Self-Healing:** The system's built-in `reclaimStaleLeases()` method automatically reclaims leases older than 5 minutes, transitions them to `AMBIGUOUS`, and triggers reconciliation.
- **Manual Intervention (if required):**
  ```sql
  UPDATE scheduled_publish_jobs 
  SET status = 'QUEUED', locked_by = NULL, locked_at = NULL 
  WHERE status = 'IN_FLIGHT' AND locked_at < NOW() - INTERVAL '10 minutes';
  ```

### Scenario B: Meta Graph API Rate Limiting (HTTP 429)
- **Behavior:** The worker classifies HTTP 429 errors under `RATE_LIMIT` category.
- **Action:** The exponential backoff policy automatically increases delay (e.g. 5m, 15m, 1h). No action required unless platform outage lasts > 24 hours.

### Scenario C: Token Expiration / Auth Revocation
- **Behavior:** The worker classifies `AUTH_FAILURE` as non-retryable and moves the job directly to `DEAD_LETTER` to prevent repeated failed calls.
- **Action:** Notify workspace owner in the Android app to re-authenticate their social account.
