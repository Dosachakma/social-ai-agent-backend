-- Migration: 004_distributed_rate_limit.sql
-- Description: Distributed rate limiting storage for horizontally scaled multi-instance deployments.
-- Author: Social Studio Security & Backend Architecture Team

-- Distributed Rate Limits Table
-- Tracks request counts and sliding-window expiration across multiple backend instances.
-- Uses atomic upsert statements for race-condition-free counting under concurrency.
CREATE TABLE IF NOT EXISTS rate_limits (
    key VARCHAR(255) PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 1,
    reset_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for efficient expiration queries and opportunistic background cleanup
CREATE INDEX IF NOT EXISTS idx_rate_limits_reset_at ON rate_limits (reset_at);
