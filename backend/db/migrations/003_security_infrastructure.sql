-- Migration: 003_security_infrastructure.sql
-- Description: Distributed OAuth ticket storage and security infrastructure for horizontally scaled production.
-- Author: Social Studio Security & Backend Architecture Team

-- Enable cryptographic extensions if not already enabled
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. OAuth Tickets Table
-- Distributed single-use OAuth authorization ticket storage across multiple backend instances.
-- Stores SHA-256 ticket hashes, encrypted session payloads, expiration timestamps, and atomic consumption markers.
CREATE TABLE IF NOT EXISTS oauth_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_hash VARCHAR(64) UNIQUE NOT NULL, -- SHA-256 hash of the secure single-use ticket token
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_data JSONB NOT NULL DEFAULT '{}'::jsonb, -- Stores encrypted accessToken, state, accountMetadata, etc.
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for lightning-fast atomic ticket lookup and background cleanup
CREATE INDEX IF NOT EXISTS idx_oauth_tickets_hash_active ON oauth_tickets (ticket_hash) WHERE consumed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_oauth_tickets_expires_at ON oauth_tickets (expires_at);
CREATE INDEX IF NOT EXISTS idx_oauth_tickets_created_at ON oauth_tickets (created_at DESC);
