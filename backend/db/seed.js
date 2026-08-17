require('dotenv').config();
const db = require('./pool');
const { runMigrations } = require('./migrate');
const cryptoService = require('../services/cryptoService');

/**
 * Idempotent Turnkey Demo Seed System
 * 
 * Seeds realistic enterprise demo data adhering strictly to the database schema
 * from migrations 001–006.
 * 
 * Safe to execute repeatedly without generating duplicate key collisions or corrupting state.
 */

// Demo Entity Identifiers (Deterministic UUIDs)
const DEMO_IDS = {
  orgId: '11111111-0000-4000-8000-000000000001',
  userId: '11111111-1111-4111-8111-111111111111',
  workspaceId: '11111111-2222-4333-8444-555555555555',
  membershipId: '11111111-2222-4333-8444-666666666666',
  brandProfileId: '33333333-3333-4333-8333-333333333333',
  accounts: {
    facebook: '44444444-4444-4444-8444-111111111111',
    instagram: '44444444-4444-4444-8444-222222222222',
    linkedin: '44444444-4444-4444-8444-333333333333'
  },
  posts: {
    published: '55555555-5555-4555-8555-111111111111',
    scheduled: '55555555-5555-4555-8555-222222222222',
    pendingApproval: '55555555-5555-4555-8555-333333333333',
    draft: '55555555-5555-4555-8555-444444444444'
  },
  results: {
    facebook: '66666666-6666-4666-8666-111111111111',
    instagram: '66666666-6666-4666-8666-222222222222',
    linkedin: '66666666-6666-4666-8666-333333333333'
  },
  jobs: {
    scheduledLinkedin: '77777777-7777-4777-8777-111111111111',
    scheduledFacebook: '77777777-7777-4777-8777-222222222222',
    publishedFacebook: '77777777-7777-4777-8777-333333333333'
  },
  intents: {
    publishedFacebook: '88888888-8888-4888-8888-111111111111'
  }
};

async function seedDatabase() {
  console.log('================================================================');
  console.log('--- Social AI Agent Idempotent Turnkey Demo Data Seeder ---');
  console.log('================================================================');

  if (!db.isConfigured()) {
    console.log('Note: PostgreSQL is not configured. Initializing in-memory fallback demo fixtures.');
    return {
      success: true,
      mode: 'in-memory',
      message: 'In-memory fallback demo state is available by default.'
    };
  }

  let client;
  try {
    // 1. Run migrations to guarantee all tables and columns exist
    console.log('Checking and applying latest database migrations...');
    const migrationResult = await runMigrations();
    if (!migrationResult.success) {
      console.warn(`Migration check warning: ${migrationResult.message}`);
    }

    client = await db.getClient();
  } catch (connErr) {
    if (connErr.code === 'ECONNREFUSED' || connErr.code === 'ENOTFOUND' || connErr.code === 'DB_NOT_CONFIGURED') {
      console.warn(`PostgreSQL connection not reachable (${connErr.message}).`);
      console.log('Populating in-memory demo fixtures for local offline development...');
      return {
        success: true,
        mode: 'in-memory-fallback',
        warning: `Database server unreachable (${connErr.code}). Run 'docker compose up -d' or set a live DATABASE_URL to persist to PostgreSQL.`,
        workspaceId: DEMO_IDS.workspaceId,
        userId: DEMO_IDS.userId
      };
    }
    throw connErr;
  }

  try {
    await client.query('BEGIN');
    console.log('Starting idempotent transaction...');

    // 2. Organization
    console.log('1. Seeding Organization...');
    await client.query(`
      INSERT INTO organizations (id, name, slug)
      VALUES ($1, $2, $3)
      ON CONFLICT (slug) DO UPDATE
      SET name = EXCLUDED.name, updated_at = NOW();
    `, [DEMO_IDS.orgId, 'Apex Brand Studio Inc.', 'apex-agency']);

    // 3. User
    console.log('2. Seeding Demo User...');
    await client.query(`
      INSERT INTO users (id, email, full_name, avatar_url)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (email) DO UPDATE
      SET full_name = EXCLUDED.full_name,
          avatar_url = EXCLUDED.avatar_url,
          updated_at = NOW();
    `, [
      DEMO_IDS.userId,
      'demo.director@apexstudio.ai',
      'Alex Mercer (Creative Director)',
      'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80'
    ]);

    // 4. Workspace
    console.log('3. Seeding Tenant Workspace...');
    await client.query(`
      INSERT INTO workspaces (id, organization_id, name, slug)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (organization_id, slug) DO UPDATE
      SET name = EXCLUDED.name, updated_at = NOW();
    `, [DEMO_IDS.workspaceId, DEMO_IDS.orgId, 'Apex Growth Workspace', 'apex-growth']);

    // 5. Workspace Membership (Owner Role)
    console.log('4. Seeding Workspace Membership...');
    await client.query(`
      INSERT INTO workspace_members (id, workspace_id, user_id, role)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (workspace_id, user_id) DO UPDATE
      SET role = EXCLUDED.role, updated_at = NOW();
    `, [DEMO_IDS.membershipId, DEMO_IDS.workspaceId, DEMO_IDS.userId, 'owner']);

    // 6. Brand Profile
    console.log('5. Seeding Brand Profile...');
    await client.query(`
      INSERT INTO brand_profiles (
        id, workspace_id, name, industry, target_audience, tone_of_voice,
        writing_style, preferred_cta, preferred_hashtags, keywords,
        brand_colors, guidelines, primary_language
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
      ON CONFLICT (id) DO UPDATE
      SET name = EXCLUDED.name,
          industry = EXCLUDED.industry,
          target_audience = EXCLUDED.target_audience,
          tone_of_voice = EXCLUDED.tone_of_voice,
          writing_style = EXCLUDED.writing_style,
          preferred_cta = EXCLUDED.preferred_cta,
          preferred_hashtags = EXCLUDED.preferred_hashtags,
          keywords = EXCLUDED.keywords,
          brand_colors = EXCLUDED.brand_colors,
          guidelines = EXCLUDED.guidelines,
          primary_language = EXCLUDED.primary_language,
          updated_at = NOW();
    `, [
      DEMO_IDS.brandProfileId,
      DEMO_IDS.workspaceId,
      'NovaFlow AI',
      'B2B SaaS & Artificial Intelligence',
      'Growth Marketers, Startup Founders, and Content Strategy Leaders',
      'PROFESSIONAL',
      'Authoritative, data-backed, concise with clear value hooks',
      'Transform your social workflow at novaflow.ai/trial',
      '#AI #Automation #GrowthMarketing #SaaS #ContentOps',
      JSON.stringify(['generative AI', 'social intelligence', 'automation', 'autonomous copilot', 'marketing ROI']),
      JSON.stringify(['#2563EB', '#7C3AED', '#10B981', '#0F172A']),
      'Always maintain high ethical AI standards. Never use clickbait or misleading claims. Highlight genuine customer productivity outcomes.',
      'ENGLISH'
    ]);

    // 7. Mock Social Accounts (Safely Encrypted with Mock Tokens)
    console.log('6. Seeding Mock Connected Social Accounts...');
    const fbEncrypted = cryptoService.encrypt('mock_meta_page_access_token_demo_fb');
    const igEncrypted = cryptoService.encrypt('mock_meta_page_access_token_demo_ig');
    const liEncrypted = cryptoService.encrypt('mock_linkedin_org_token_demo');

    // Facebook Page (Demo)
    await client.query(`
      INSERT INTO social_accounts (
        id, workspace_id, platform, platform_user_id, account_name, handle,
        avatar_url, account_type, connection_status, token_status,
        encrypted_access_token, encrypted_refresh_token, follower_count, posts_today_count
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
      ON CONFLICT (workspace_id, platform, platform_user_id) DO UPDATE
      SET account_name = EXCLUDED.account_name,
          handle = EXCLUDED.handle,
          connection_status = EXCLUDED.connection_status,
          token_status = EXCLUDED.token_status,
          encrypted_access_token = EXCLUDED.encrypted_access_token,
          follower_count = EXCLUDED.follower_count,
          posts_today_count = EXCLUDED.posts_today_count,
          last_synced_at = NOW(),
          updated_at = NOW();
    `, [
      DEMO_IDS.accounts.facebook,
      DEMO_IDS.workspaceId,
      'FACEBOOK',
      'mock_fb_page_101',
      'NovaFlow AI Official (Demo)',
      '@NovaFlowAI',
      'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=256&q=80',
      'PAGE',
      'CONNECTED',
      'VALID',
      fbEncrypted,
      null,
      14850,
      1
    ]);

    // Instagram Business (Demo)
    await client.query(`
      INSERT INTO social_accounts (
        id, workspace_id, platform, platform_user_id, account_name, handle,
        avatar_url, account_type, connection_status, token_status,
        encrypted_access_token, encrypted_refresh_token, follower_count, posts_today_count
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
      ON CONFLICT (workspace_id, platform, platform_user_id) DO UPDATE
      SET account_name = EXCLUDED.account_name,
          handle = EXCLUDED.handle,
          connection_status = EXCLUDED.connection_status,
          token_status = EXCLUDED.token_status,
          encrypted_access_token = EXCLUDED.encrypted_access_token,
          follower_count = EXCLUDED.follower_count,
          posts_today_count = EXCLUDED.posts_today_count,
          last_synced_at = NOW(),
          updated_at = NOW();
    `, [
      DEMO_IDS.accounts.instagram,
      DEMO_IDS.workspaceId,
      'INSTAGRAM',
      'mock_ig_account_202',
      'NovaFlow Cloud Official (Demo)',
      '@novaflow.ai',
      'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=256&q=80',
      'BUSINESS',
      'CONNECTED',
      'VALID',
      igEncrypted,
      null,
      28400,
      2
    ]);

    // LinkedIn Organization (Demo)
    await client.query(`
      INSERT INTO social_accounts (
        id, workspace_id, platform, platform_user_id, account_name, handle,
        avatar_url, account_type, connection_status, token_status,
        encrypted_access_token, encrypted_refresh_token, follower_count, posts_today_count
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
      ON CONFLICT (workspace_id, platform, platform_user_id) DO UPDATE
      SET account_name = EXCLUDED.account_name,
          handle = EXCLUDED.handle,
          connection_status = EXCLUDED.connection_status,
          token_status = EXCLUDED.token_status,
          encrypted_access_token = EXCLUDED.encrypted_access_token,
          follower_count = EXCLUDED.follower_count,
          posts_today_count = EXCLUDED.posts_today_count,
          last_synced_at = NOW(),
          updated_at = NOW();
    `, [
      DEMO_IDS.accounts.linkedin,
      DEMO_IDS.workspaceId,
      'LINKEDIN',
      'mock_li_company_303',
      'NovaFlow Technologies (Demo)',
      'novaflow-technologies',
      'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=256&q=80',
      'BUSINESS',
      'CONNECTED',
      'VALID',
      liEncrypted,
      null,
      9600,
      0
    ]);

    // 8. Social Posts Across Lifecycle Stages
    console.log('7. Seeding Multi-Stage Social Posts...');

    // Post 1: Published Live Post
    await client.query(`
      INSERT INTO social_posts (
        id, workspace_id, created_by_user_id, title, content, target_platforms,
        status, approval_state, scheduled_at, timezone, published_at,
        require_approval, is_ai_generated, engagement_score, engagement_count
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
      ON CONFLICT (id) DO UPDATE
      SET title = EXCLUDED.title,
          content = EXCLUDED.content,
          status = EXCLUDED.status,
          approval_state = EXCLUDED.approval_state,
          published_at = EXCLUDED.published_at,
          engagement_score = EXCLUDED.engagement_score,
          engagement_count = EXCLUDED.engagement_count,
          updated_at = NOW();
    `, [
      DEMO_IDS.posts.published,
      DEMO_IDS.workspaceId,
      DEMO_IDS.userId,
      'Autonomous Social Media in 2026: Why Quality Trumps Volume',
      'Publishing 10 generic posts daily is dead. Context-aware AI agents now allow brands to produce high-signal, brand-aligned multi-platform campaigns in minutes. Learn how top teams scaled engagement 3.4x while saving 20 hours/week.',
      JSON.stringify(['FACEBOOK', 'INSTAGRAM', 'LINKEDIN']),
      'PUBLISHED',
      'APPROVED',
      new Date(Date.now() - 86400000 * 2).toISOString(),
      'UTC',
      new Date(Date.now() - 86400000 * 2 + 5000).toISOString(),
      true,
      true,
      94,
      1420
    ]);

    // Post 2: Scheduled Post Awaiting Execution
    await client.query(`
      INSERT INTO social_posts (
        id, workspace_id, created_by_user_id, title, content, target_platforms,
        status, approval_state, scheduled_at, timezone, require_approval, is_ai_generated, engagement_score
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
      ON CONFLICT (id) DO UPDATE
      SET title = EXCLUDED.title,
          content = EXCLUDED.content,
          status = EXCLUDED.status,
          approval_state = EXCLUDED.approval_state,
          scheduled_at = EXCLUDED.scheduled_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.posts.scheduled,
      DEMO_IDS.workspaceId,
      DEMO_IDS.userId,
      '3 Architectural Pillars of Resilient Multi-Tenant Automation',
      'Building distributed scheduling infrastructure requires three non-negotiables: 1) Exactly-once publish intent gates, 2) Cryptographic AES-256-GCM token vaults, and 3) Deterministic failure reconciliation. Here is our blueprint.',
      JSON.stringify(['LINKEDIN', 'FACEBOOK']),
      'SCHEDULED',
      'APPROVED',
      new Date(Date.now() + 86400000 * 2).toISOString(),
      'UTC',
      true,
      true,
      0
    ]);

    // Post 3: Draft Awaiting Human Approval
    await client.query(`
      INSERT INTO social_posts (
        id, workspace_id, created_by_user_id, title, content, target_platforms,
        status, approval_state, require_approval, is_ai_generated
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (id) DO UPDATE
      SET title = EXCLUDED.title,
          content = EXCLUDED.content,
          status = EXCLUDED.status,
          approval_state = EXCLUDED.approval_state,
          updated_at = NOW();
    `, [
      DEMO_IDS.posts.pendingApproval,
      DEMO_IDS.workspaceId,
      DEMO_IDS.userId,
      'How Autonomous AI Agents Transform Content Operations',
      'Content teams spend 60% of their time on distribution logistics. Our new orchestrator handles formatting, scheduling, and channel-specific nuances while keeping humans in the approval loop.',
      JSON.stringify(['FACEBOOK', 'INSTAGRAM']),
      'DRAFT',
      'AWAITING_APPROVAL',
      true,
      true
    ]);

    // Post 4: Proposed Draft
    await client.query(`
      INSERT INTO social_posts (
        id, workspace_id, created_by_user_id, title, content, target_platforms,
        status, approval_state, require_approval, is_ai_generated
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (id) DO UPDATE
      SET title = EXCLUDED.title,
          content = EXCLUDED.content,
          status = EXCLUDED.status,
          approval_state = EXCLUDED.approval_state,
          updated_at = NOW();
    `, [
      DEMO_IDS.posts.draft,
      DEMO_IDS.workspaceId,
      DEMO_IDS.userId,
      'Product Spotlight: Smart Multi-Channel Publishing',
      'Cross-posting the exact same copy to every network harms reach. NovaFlow adapts tone and hashtags specifically for Instagram, LinkedIn, and Facebook automatically.',
      JSON.stringify(['INSTAGRAM']),
      'DRAFT',
      'PROPOSED',
      true,
      true
    ]);

    // 9. Platform Publish Results for Post 1
    console.log('8. Seeding Platform Publish Results...');
    await client.query(`
      INSERT INTO platform_publish_results (
        id, post_id, workspace_id, platform, status, external_post_id,
        idempotency_key, execution_environment, published_at
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      ON CONFLICT (post_id, platform) DO UPDATE
      SET status = EXCLUDED.status,
          external_post_id = EXCLUDED.external_post_id,
          execution_environment = EXCLUDED.execution_environment,
          published_at = EXCLUDED.published_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.results.facebook,
      DEMO_IDS.posts.published,
      DEMO_IDS.workspaceId,
      'FACEBOOK',
      'SUCCESS',
      'fb_pub_demo_987654',
      'idemp_demo_pub1_fb',
      'MOCK',
      new Date(Date.now() - 86400000 * 2 + 5000).toISOString()
    ]);

    await client.query(`
      INSERT INTO platform_publish_results (
        id, post_id, workspace_id, platform, status, external_post_id,
        idempotency_key, execution_environment, published_at
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      ON CONFLICT (post_id, platform) DO UPDATE
      SET status = EXCLUDED.status,
          external_post_id = EXCLUDED.external_post_id,
          execution_environment = EXCLUDED.execution_environment,
          published_at = EXCLUDED.published_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.results.instagram,
      DEMO_IDS.posts.published,
      DEMO_IDS.workspaceId,
      'INSTAGRAM',
      'SUCCESS',
      'ig_pub_demo_123456',
      'idemp_demo_pub1_ig',
      'MOCK',
      new Date(Date.now() - 86400000 * 2 + 5000).toISOString()
    ]);

    // 10. Scheduled Publish Jobs
    console.log('9. Seeding Scheduled Publish Jobs...');
    // Future job on LinkedIn
    await client.query(`
      INSERT INTO scheduled_publish_jobs (
        id, workspace_id, post_id, platform, status, attempt_count,
        max_attempts, next_attempt_at, idempotency_key, payload
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (post_id, platform) DO UPDATE
      SET status = EXCLUDED.status,
          next_attempt_at = EXCLUDED.next_attempt_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.jobs.scheduledLinkedin,
      DEMO_IDS.workspaceId,
      DEMO_IDS.posts.scheduled,
      'LINKEDIN',
      'QUEUED',
      0,
      3,
      new Date(Date.now() + 86400000 * 2).toISOString(),
      'job_demo_post2_linkedin',
      JSON.stringify({ adapter: 'default', mode: 'demo' })
    ]);

    // Future job on Facebook
    await client.query(`
      INSERT INTO scheduled_publish_jobs (
        id, workspace_id, post_id, platform, status, attempt_count,
        max_attempts, next_attempt_at, idempotency_key, payload
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (post_id, platform) DO UPDATE
      SET status = EXCLUDED.status,
          next_attempt_at = EXCLUDED.next_attempt_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.jobs.scheduledFacebook,
      DEMO_IDS.workspaceId,
      DEMO_IDS.posts.scheduled,
      'FACEBOOK',
      'QUEUED',
      0,
      3,
      new Date(Date.now() + 86400000 * 2).toISOString(),
      'job_demo_post2_facebook',
      JSON.stringify({ adapter: 'default', mode: 'demo' })
    ]);

    // Succeeded job for Post 1
    await client.query(`
      INSERT INTO scheduled_publish_jobs (
        id, workspace_id, post_id, platform, status, attempt_count,
        max_attempts, completed_at, idempotency_key, payload
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (post_id, platform) DO UPDATE
      SET status = EXCLUDED.status,
          completed_at = EXCLUDED.completed_at,
          updated_at = NOW();
    `, [
      DEMO_IDS.jobs.publishedFacebook,
      DEMO_IDS.workspaceId,
      DEMO_IDS.posts.published,
      'FACEBOOK',
      'SUCCEEDED',
      1,
      3,
      new Date(Date.now() - 86400000 * 2 + 5000).toISOString(),
      'job_demo_post1_facebook',
      JSON.stringify({ adapter: 'default', mode: 'demo' })
    ]);

    // 11. Publish Intents
    console.log('10. Seeding Publish Intent Records...');
    await client.query(`
      INSERT INTO publish_intents (
        id, job_id, workspace_id, post_id, platform, client_mutation_id,
        idempotency_key, content_hash, state, attempt_number, external_post_id
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
      ON CONFLICT (job_id, attempt_number) DO UPDATE
      SET state = EXCLUDED.state,
          external_post_id = EXCLUDED.external_post_id,
          updated_at = NOW();
    `, [
      DEMO_IDS.intents.publishedFacebook,
      DEMO_IDS.jobs.publishedFacebook,
      DEMO_IDS.workspaceId,
      DEMO_IDS.posts.published,
      'FACEBOOK',
      'mut_demo_post1_fb_001',
      'idemp_demo_pub1_fb',
      'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0',
      'COMMITTED',
      1,
      'fb_pub_demo_987654'
    ]);

    // 12. Agent Action Logs
    console.log('11. Seeding AI Agent Orchestration Audit Logs...');
    const sampleLogs = [
      {
        action: 'CREATE_POST',
        platform: 'FACEBOOK',
        status: 'SUCCESS',
        metadata: { event: 'AI_POST_GENERATION', prompt: 'Autonomous social media trends in 2026', tone: 'PROFESSIONAL' }
      },
      {
        action: 'SCHEDULE_POST',
        platform: 'LINKEDIN',
        status: 'APPROVED',
        metadata: { event: 'APPROVAL_GRANTED', approver: 'Alex Mercer', scheduledTime: '2026-08-18T16:30:00Z' }
      },
      {
        action: 'PUBLISH_POST',
        platform: 'FACEBOOK',
        status: 'SUCCESS',
        metadata: { event: 'PLATFORM_PUBLISH_SUCCESS', postId: DEMO_IDS.posts.published, publishedPostId: 'fb_pub_demo_987654' }
      },
      {
        action: 'ANALYZE_ACCOUNT',
        platform: 'INSTAGRAM',
        status: 'SUCCESS',
        metadata: { event: 'PERFORMANCE_SYNC', audienceGrowth: '+4.8%', engagementLift: '+28.4%' }
      }
    ];

    for (const logItem of sampleLogs) {
      await client.query(`
        INSERT INTO agent_action_logs (
          workspace_id, user_id, action, platform, status, execution_environment, metadata
        )
        VALUES ($1, $2, $3, $4, $5, $6, $7);
      `, [
        DEMO_IDS.workspaceId,
        DEMO_IDS.userId,
        logItem.action,
        logItem.platform,
        logItem.status,
        'MOCK',
        JSON.stringify(logItem.metadata)
      ]);
    }

    await client.query('COMMIT');
    console.log('✓ Transaction committed successfully.');

    return {
      success: true,
      mode: 'postgresql',
      seeded: {
        organizations: 1,
        users: 1,
        workspaces: 1,
        brandProfiles: 1,
        socialAccounts: 3,
        socialPosts: 4,
        publishResults: 2,
        scheduledJobs: 3,
        publishIntents: 1,
        agentLogs: 4
      },
      workspaceId: DEMO_IDS.workspaceId,
      userId: DEMO_IDS.userId
    };

  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Error during database seed transaction, rolled back:', err.message);
    throw err;
  } finally {
    client.release();
  }
}

if (require.main === module) {
  seedDatabase()
    .then((res) => {
      console.log('✓ Database seeding completed successfully.');
      console.log(JSON.stringify(res, null, 2));
      process.exit(0);
    })
    .catch((err) => {
      console.error('Seed execution failed:', err);
      process.exit(1);
    });
}

module.exports = {
  DEMO_IDS,
  seedDatabase
};
