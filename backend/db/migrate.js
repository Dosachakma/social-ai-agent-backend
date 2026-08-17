const fs = require('fs');
const path = require('path');
const db = require('./pool');

/**
 * Migration runner for PostgreSQL schema management.
 * Tracks applied migrations in the `schema_migrations` table.
 */
async function runMigrations() {
  if (!db.isConfigured()) {
    return {
      success: false,
      message: 'Database is not configured. Set DATABASE_URL or PG environment variables to run migrations.',
      appliedCount: 0
    };
  }

  let client;
  try {
    client = await db.getClient();
  } catch (connErr) {
    if (connErr.code === 'ECONNREFUSED' || connErr.code === 'ENOTFOUND' || connErr.code === 'DB_NOT_CONFIGURED') {
      console.warn(`PostgreSQL server is not reachable (${connErr.message}).`);
      return {
        success: false,
        mode: 'offline',
        warning: `Database server unreachable (${connErr.code}). Run 'docker compose up -d' or set a live DATABASE_URL to apply migrations to PostgreSQL.`,
        appliedCount: 0,
        applied: []
      };
    }
    throw connErr;
  }

  try {
    // 1. Create migration tracking table if it doesn't exist
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version VARCHAR(255) PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
    `);

    // 2. Fetch already applied migrations
    const res = await client.query('SELECT version FROM schema_migrations ORDER BY version ASC');
    const appliedVersions = new Set(res.rows.map(r => r.version));

    // 3. Read migration files
    const migrationsDir = path.join(__dirname, 'migrations');
    if (!fs.existsSync(migrationsDir)) {
      return { success: true, message: 'No migrations directory found', appliedCount: 0, applied: [] };
    }

    const files = fs.readdirSync(migrationsDir)
      .filter(f => f.endsWith('.sql'))
      .sort();

    const newlyApplied = [];

    for (const file of files) {
      const version = file.split('_')[0];
      if (appliedVersions.has(version)) {
        continue;
      }

      console.log(`Applying migration: ${file}...`);
      const sqlContent = fs.readFileSync(path.join(migrationsDir, file), 'utf8');

      // Execute migration within transaction
      await client.query('BEGIN');
      try {
        await client.query(sqlContent);
        await client.query(
          'INSERT INTO schema_migrations (version, name) VALUES ($1, $2)',
          [version, file]
        );
        await client.query('COMMIT');
        newlyApplied.push(file);
        console.log(`✓ Successfully applied: ${file}`);
      } catch (err) {
        await client.query('ROLLBACK');
        console.error(`✗ Error applying migration ${file}:`, err.message);
        throw new Error(`Migration ${file} failed: ${err.message}`);
      }
    }

    return {
      success: true,
      appliedCount: newlyApplied.length,
      applied: newlyApplied,
      message: newlyApplied.length > 0 
        ? `Applied ${newlyApplied.length} migration(s) successfully.` 
        : 'Database schema is already up to date.'
    };
  } finally {
    client.release();
  }
}

// Run directly if invoked from command line
if (require.main === module) {
  require('dotenv').config();
  runMigrations()
    .then((result) => {
      console.log(JSON.stringify(result, null, 2));
      process.exit(result.success || result.mode === 'offline' ? 0 : 1);
    })
    .catch((err) => {
      console.error('Fatal migration error:', err);
      process.exit(1);
    });
}

module.exports = {
  runMigrations
};
