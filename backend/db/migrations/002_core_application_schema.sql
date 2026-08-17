-- Migration: 002_core_application_schema.sql
-- Description: Core application schema for social_accounts, social_posts, platform_publish_results, agent_action_logs, and brand_profile extensions.
-- Author: Social Studio Architecture Team

-- Enable cryptographic extensions if not already enabled
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Extend Brand Profiles Table if needed
ALTER TABLE brand_profiles 
    ADD COLUMN IF NOT EXISTS primary_language VARCHAR(50) DEFAULT 'ENGLISH',
    ADD COLUMN IF NOT EXISTS secondary_language VARCHAR(50),
    ADD COLUMN IF NOT EXISTS writing_style TEXT,
    ADD COLUMN IF NOT EXISTS preferred_cta TEXT,
    ADD COLUMN IF NOT EXISTS preferred_hashtags TEXT,
    ADD COLUMN IF NOT EXISTS words_to_avoid TEXT,
    ADD COLUMN IF NOT EXISTS products_services TEXT,
    ADD COLUMN IF NOT EXISTS website TEXT,
    ADD COLUMN IF NOT EXISTS contact_info TEXT;

-- 2. Social Accounts Table (Multi-tenant social connections per workspace)
CREATE TABLE IF NOT EXISTS social_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL, -- 'FACEBOOK', 'INSTAGRAM', 'TWITTER', 'LINKEDIN', 'TIKTOK'
    platform_user_id VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    handle VARCHAR(255) NOT NULL,
    avatar_url TEXT DEFAULT '',
    account_type VARCHAR(50) NOT NULL DEFAULT 'PAGE', -- 'PERSONAL', 'PAGE', 'BUSINESS', 'CREATOR'
    connection_status VARCHAR(50) NOT NULL DEFAULT 'CONNECTED', -- 'CONNECTED', 'DISCONNECTED', 'EXPIRED', 'ERROR'
    token_status VARCHAR(50) NOT NULL DEFAULT 'VALID', -- 'VALID', 'EXPIRING', 'EXPIRED', 'REVOKED'
    encrypted_access_token TEXT, -- Sensitive: Never exposed in API responses or logs
    encrypted_refresh_token TEXT, -- Sensitive: Never exposed in API responses or logs
    token_expires_at TIMESTAMPTZ,
    scopes JSONB DEFAULT '[]'::jsonb,
    capabilities JSONB DEFAULT '["CREATE_POST", "PUBLISH_POST", "READ_COMMENTS", "REPLY_COMMENT", "READ_ANALYTICS", "MEDIA_UPLOAD"]'::jsonb,
    follower_count INT NOT NULL DEFAULT 0,
    posts_today_count INT NOT NULL DEFAULT 0,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_accounts_ws_plat_user UNIQUE (workspace_id, platform, platform_user_id)
);

CREATE INDEX IF NOT EXISTS idx_social_accounts_workspace_id ON social_accounts (workspace_id);
CREATE INDEX IF NOT EXISTS idx_social_accounts_platform ON social_accounts (workspace_id, platform);

-- 3. Social Posts Table (Posts, Drafts, Scheduled Posts)
CREATE TABLE IF NOT EXISTS social_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_platforms JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT', 'SCHEDULED', 'PUBLISHED', 'FAILED', 'GENERATING'
    approval_state VARCHAR(50) NOT NULL DEFAULT 'PROPOSED', -- 'PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'EXECUTING', 'SUCCESS', 'FAILED', 'CANCELLED'
    scheduled_at TIMESTAMPTZ,
    scheduled_time VARCHAR(100),
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    repeat_option VARCHAR(50) NOT NULL DEFAULT 'NONE', -- 'NONE', 'DAILY', 'WEEKLY', 'MONTHLY'
    require_approval BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMPTZ,
    media_urls JSONB DEFAULT '[]'::jsonb,
    hashtags TEXT DEFAULT '',
    cta TEXT DEFAULT '',
    is_ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    engagement_score INT NOT NULL DEFAULT 0,
    engagement_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_social_posts_workspace_id ON social_posts (workspace_id);
CREATE INDEX IF NOT EXISTS idx_social_posts_status ON social_posts (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_social_posts_scheduled_at ON social_posts (workspace_id, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_social_posts_created_at ON social_posts (workspace_id, created_at DESC);

-- 4. Platform Publish Results Table
CREATE TABLE IF NOT EXISTS platform_publish_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL, -- 'FACEBOOK', 'INSTAGRAM', 'TWITTER', 'LINKEDIN', 'TIKTOK'
    status VARCHAR(50) NOT NULL DEFAULT 'PROPOSED', -- 'PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'EXECUTING', 'SUCCESS', 'FAILED', 'CANCELLED'
    external_post_id VARCHAR(255),
    error_message TEXT,
    idempotency_key VARCHAR(255),
    execution_environment VARCHAR(50) NOT NULL DEFAULT 'MOCK',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_publish_results_post_platform UNIQUE (post_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_publish_results_post_id ON platform_publish_results (post_id);
CREATE INDEX IF NOT EXISTS idx_publish_results_workspace_id ON platform_publish_results (workspace_id);

-- 5. Agent Action Logs Table
CREATE TABLE IF NOT EXISTS agent_action_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL, -- 'CREATE_POST', 'GENERATE_IMAGE', 'SCHEDULE_POST', 'PUBLISH_POST', 'REPLY_COMMENT', 'REPLY_MESSAGE', 'ANALYZE_ACCOUNT'
    platform VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PROPOSED', -- 'PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'EXECUTING', 'SUCCESS', 'FAILED', 'CANCELLED'
    execution_environment VARCHAR(50) NOT NULL DEFAULT 'MOCK',
    error TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_action_logs_workspace_id ON agent_action_logs (workspace_id);
CREATE INDEX IF NOT EXISTS idx_agent_action_logs_created_at ON agent_action_logs (workspace_id, created_at DESC);
