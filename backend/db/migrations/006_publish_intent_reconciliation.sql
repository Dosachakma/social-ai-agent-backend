-- Migration: 006_publish_intent_reconciliation.sql
-- Description: Durable Publish Intent Gate & Reconciliation Storage for Exactly-Once Intent.
-- Author: Social Studio Architecture Team

-- 1. Update check constraint on scheduled_publish_jobs to include 'AMBIGUOUS'
ALTER TABLE scheduled_publish_jobs DROP CONSTRAINT IF EXISTS chk_scheduled_jobs_status;

ALTER TABLE scheduled_publish_jobs ADD CONSTRAINT chk_scheduled_jobs_status CHECK (
    status IN ('QUEUED', 'CLAIMED', 'RUNNING', 'INTENT_LOCKED', 'SUCCEEDED', 'RETRYING', 'FAILED', 'DEAD_LETTER', 'CANCELLED', 'AMBIGUOUS')
);

-- 2. Publish Intents Table (Durable intent gate before external platform network dispatch)
CREATE TABLE IF NOT EXISTS publish_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES scheduled_publish_jobs(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    client_mutation_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'CREATED', -- 'CREATED', 'IN_FLIGHT', 'COMMITTED', 'AMBIGUOUS', 'RECONCILED'
    attempt_number INT NOT NULL DEFAULT 1,
    sent_at TIMESTAMPTZ,
    response_received_at TIMESTAMPTZ,
    reconciliation_attempts INT NOT NULL DEFAULT 0,
    last_reconciled_at TIMESTAMPTZ,
    external_post_id VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb, -- non-sensitive payload/headers metadata, NO TOKENS
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_publish_intents_job_attempt UNIQUE (job_id, attempt_number),
    CONSTRAINT chk_publish_intents_state CHECK (
        state IN ('CREATED', 'IN_FLIGHT', 'COMMITTED', 'AMBIGUOUS', 'RECONCILED')
    )
);

-- Indexes for reconciliation lookups and tenant queries
CREATE INDEX IF NOT EXISTS idx_publish_intents_workspace_post ON publish_intents (workspace_id, post_id);
CREATE INDEX IF NOT EXISTS idx_publish_intents_job_id ON publish_intents (job_id);
CREATE INDEX IF NOT EXISTS idx_publish_intents_state ON publish_intents (state);
CREATE INDEX IF NOT EXISTS idx_publish_intents_mutation_id ON publish_intents (client_mutation_id);
CREATE INDEX IF NOT EXISTS idx_publish_intents_content_hash ON publish_intents (content_hash);
