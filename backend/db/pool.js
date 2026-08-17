const { Pool } = require('pg');

/**
 * Production-safe PostgreSQL Database Connection Pool
 * Configured strictly via environment variables. Credentials are never hardcoded.
 */

let pool = null;

function getPoolConfig() {
  // Option 1: Full connection string (DATABASE_URL)
  if (process.env.DATABASE_URL) {
    const isProduction = process.env.NODE_ENV === 'production';
    const sslMode = process.env.PGSSLMODE || process.env.DB_SSL;
    
    let ssl = undefined;
    if (sslMode === 'require' || sslMode === 'true' || isProduction) {
      ssl = {
        rejectUnauthorized: process.env.PGSSL_REJECT_UNAUTHORIZED === 'true'
      };
    }

    return {
      connectionString: process.env.DATABASE_URL,
      ssl,
      max: parseInt(process.env.DB_POOL_MAX || '10', 10),
      idleTimeoutMillis: parseInt(process.env.DB_IDLE_TIMEOUT_MS || '30000', 10),
      connectionTimeoutMillis: parseInt(process.env.DB_CONNECTION_TIMEOUT_MS || '5000', 10)
    };
  }

  // Option 2: Individual environment variables
  if (process.env.PGHOST || process.env.DB_HOST) {
    const isProduction = process.env.NODE_ENV === 'production';
    const sslMode = process.env.PGSSLMODE || process.env.DB_SSL;
    
    let ssl = undefined;
    if (sslMode === 'require' || sslMode === 'true' || (isProduction && sslMode !== 'disable')) {
      ssl = {
        rejectUnauthorized: process.env.PGSSL_REJECT_UNAUTHORIZED === 'true'
      };
    }

    return {
      host: process.env.PGHOST || process.env.DB_HOST,
      port: parseInt(process.env.PGPORT || process.env.DB_PORT || '5432', 10),
      user: process.env.PGUSER || process.env.DB_USER,
      password: process.env.PGPASSWORD || process.env.DB_PASSWORD,
      database: process.env.PGDATABASE || process.env.DB_NAME,
      ssl,
      max: parseInt(process.env.DB_POOL_MAX || '10', 10),
      idleTimeoutMillis: parseInt(process.env.DB_IDLE_TIMEOUT_MS || '30000', 10),
      connectionTimeoutMillis: parseInt(process.env.DB_CONNECTION_TIMEOUT_MS || '5000', 10)
    };
  }

  return null;
}

function isConfigured() {
  return getPoolConfig() !== null;
}

function getPool() {
  if (pool) return pool;

  const config = getPoolConfig();
  if (!config) {
    return null;
  }

  pool = new Pool(config);

  // Safe idle error listener to prevent process crashes
  pool.on('error', (err) => {
    console.error('Unexpected error on idle PostgreSQL client:', err.message);
  });

  return pool;
}

/**
 * Execute a query against the pool with safety checks.
 */
async function query(text, params = []) {
  const currentPool = getPool();
  if (!currentPool) {
    const error = new Error('Database connection is not configured. DATABASE_URL or PG environment variables are required.');
    error.code = 'DB_NOT_CONFIGURED';
    throw error;
  }
  return await currentPool.query(text, params);
}

/**
 * Acquire a dedicated client from the pool (for transactions).
 */
async function getClient() {
  const currentPool = getPool();
  if (!currentPool) {
    const error = new Error('Database connection is not configured. DATABASE_URL or PG environment variables are required.');
    error.code = 'DB_NOT_CONFIGURED';
    throw error;
  }
  return await currentPool.connect();
}

/**
 * Database health check and latency measurement.
 */
async function healthCheck() {
  if (!isConfigured()) {
    return {
      status: 'not_configured',
      connected: false,
      message: 'PostgreSQL environment variables (DATABASE_URL or PGHOST) are not configured',
      latencyMs: null,
      timestamp: new Date().toISOString()
    };
  }

  const startTime = Date.now();
  try {
    const res = await query('SELECT 1 AS health, NOW() AS server_time, version() AS db_version');
    const latencyMs = Date.now() - startTime;
    return {
      status: 'connected',
      connected: true,
      latencyMs,
      serverTime: res.rows[0]?.server_time,
      dbVersion: res.rows[0]?.db_version,
      timestamp: new Date().toISOString()
    };
  } catch (err) {
    const latencyMs = Date.now() - startTime;
    return {
      status: 'error',
      connected: false,
      error: err.message,
      code: err.code || 'CONNECTION_ERROR',
      latencyMs,
      timestamp: new Date().toISOString()
    };
  }
}

/**
 * Gracefully drain and close the connection pool.
 */
async function closePool() {
  if (pool) {
    await pool.end();
    pool = null;
  }
}

module.exports = {
  getPool,
  getPoolConfig,
  isConfigured,
  query,
  getClient,
  healthCheck,
  closePool
};
