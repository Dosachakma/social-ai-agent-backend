-- Migration: 005_server_scheduling.sql
-- Description: Durable server-side scheduling and headless publishing job queue for multi-instance production.
-- Author: Social Studio Architecture Team

-- Enable cryptographic extensions if not already enabled
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Scheduled Publish Jobs Table (Durable per-platform job queue)
CREATE TABLE IF NOT EXISTS scheduled_publish_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL, -- 'FACEBOOK', 'INSTAGRAM', 'TWITTER', 'LINKEDIN', 'TIKTOK'
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED', -- 'QUEUED', 'CLAIMED', 'RUNNING', 'SUCCEEDED', 'RETRYING', 'FAILED', 'DEAD_LETTER', 'CANCELLED'
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(255),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb, -- strictly non-sensitive metadata, NO OAUTH TOKENS
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scheduled_jobs_post_platform UNIQUE (post_id, platform),
    CONSTRAINT uq_scheduled_jobs_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_scheduled_jobs_status CHECK (
        status IN ('QUEUED', 'CLAIMED', 'RUNNING', 'SUCCEEDED', 'RETRYING', 'FAILED', 'DEAD_LETTER', 'CANCELLED')
    )
);

-- Partial indexes for high-throughput polling and lock recovery
CREATE INDEX IF NOT EXISTS idx_sched_jobs_status_next ON scheduled_publish_jobs (status, next_attempt_at) WHERE status IN ('QUEUED', 'RETRYING');
CREATE INDEX IF NOT EXISTS idx_sched_jobs_locked_at ON scheduled_publish_jobs (locked_at) WHERE locked_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sched_jobs_workspace_id ON scheduled_publish_jobs (workspace_id);
CREATE INDEX IF NOT EXISTS idx_sched_jobs_post_id ON scheduled_publish_jobs (post_id);
CREATE INDEX IF NOT EXISTS idx_sched_jobs_platform ON scheduled_publish_jobs (workspace_id, platform);
