-- Migration: 001_initial_schema.sql
-- Description: Initial schema for organizations, users, workspaces, workspace_members, and brand_profiles.
-- Author: Social Studio Architecture Team

-- Enable cryptographic extensions for UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Organizations Table
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index on organization slug for fast subdomain/routing lookup
CREATE INDEX IF NOT EXISTS idx_organizations_slug ON organizations (slug);

-- 2. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index on user email for fast authentication and member invitations
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 3. Workspaces Table (Tenant boundary)
CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspaces_org_slug UNIQUE (organization_id, slug)
);

-- Indexes for workspace organization lookups and slugs
CREATE INDEX IF NOT EXISTS idx_workspaces_organization_id ON workspaces (organization_id);
CREATE INDEX IF NOT EXISTS idx_workspaces_slug ON workspaces (slug);

-- 4. Workspace Members Table (Multi-tenant membership & RBAC)
CREATE TABLE IF NOT EXISTS workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL DEFAULT 'member', -- Valid roles: 'owner', 'admin', 'member', 'viewer'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspace_members_user UNIQUE (workspace_id, user_id)
);

-- Indexes for fast tenant-isolation membership checks
CREATE INDEX IF NOT EXISTS idx_workspace_members_workspace_id ON workspace_members (workspace_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_user_id ON workspace_members (user_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_lookup ON workspace_members (workspace_id, user_id);

-- 5. Brand Profiles Table (Scoped strictly to workspace tenant)
CREATE TABLE IF NOT EXISTS brand_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(100),
    target_audience TEXT,
    tone_of_voice VARCHAR(100),
    keywords JSONB DEFAULT '[]'::jsonb,
    brand_colors JSONB DEFAULT '[]'::jsonb,
    guidelines TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for tenant-isolated brand profile queries
CREATE INDEX IF NOT EXISTS idx_brand_profiles_workspace_id ON brand_profiles (workspace_id);
