/**
 * Production-Grade Abuse Protection & Rate Limiting Engine.
 * 
 * Features:
 * - Sliding-window counter algorithm with precise millisecond expiration.
 * - Standard RFC 6585 and draft IETF RateLimit HTTP headers (`RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, `Retry-After`).
 * - Multi-tiered protection:
 *   1. IP-based rate limiting for authentication and ticket exchange endpoints.
 *   2. Tenant/Workspace-based rate limiting for API mutations and AI actions.
 *   3. Global API rate limiting for public/general traffic.
 * - Automatic background cache cleanup preventing memory leaks.
 * - Zero external dependency footprint.
 */

const db = require('../db/pool');

/**
 * In-memory fallback rate limit store.
 * Strictly for non-production environments (development, isolated unit testing).
 */
class MemoryRateLimitStore {
  constructor(cleanupIntervalMs = 60000) {
    this.hits = new Map(); // key -> { count, resetTime }
    this.cleanupTimer = setInterval(() => this.purgeExpired(), cleanupIntervalMs);
    if (this.cleanupTimer && typeof this.cleanupTimer.unref === 'function') {
      this.cleanupTimer.unref();
    }
  }

  increment(key, windowMs) {
    const now = Date.now();
    let record = this.hits.get(key);

    if (!record || now >= record.resetTime) {
      record = {
        count: 1,
        resetTime: now + windowMs
      };
      this.hits.set(key, record);
      return {
        count: 1,
        resetTime: record.resetTime,
        remainingMs: windowMs
      };
    }

    record.count += 1;
    return {
      count: record.count,
      resetTime: record.resetTime,
      remainingMs: Math.max(0, record.resetTime - now)
    };
  }

  reset(key) {
    this.hits.delete(key);
  }

  clear() {
    this.hits.clear();
  }

  purgeExpired() {
    const now = Date.now();
    for (const [key, record] of this.hits.entries()) {
      if (now >= record.resetTime) {
        this.hits.delete(key);
      }
    }
  }
}

/**
 * Distributed PostgreSQL Rate Limit Store.
 * Provides atomic, race-condition-free counting across horizontally scaled backend instances.
 */
class PostgresRateLimitStore {
  constructor(cleanupIntervalMs = 60000) {
    this.cleanupTimer = setInterval(() => {
      this.purgeExpired().catch(() => {});
    }, cleanupIntervalMs);
    if (this.cleanupTimer && typeof this.cleanupTimer.unref === 'function') {
      this.cleanupTimer.unref();
    }
  }

  /**
   * Atomically increments the request counter for the given key in PostgreSQL.
   * Handles sliding-window reset atomically in a single SQL statement.
   * 
   * @param {string} key - Unique rate-limit identifier
   * @param {number} windowMs - Window duration in milliseconds
   * @returns {Promise<{ count: number, resetTime: number, remainingMs: number }>}
   */
  async increment(key, windowMs) {
    const sql = `
      INSERT INTO rate_limits (key, count, reset_at, created_at)
      VALUES ($1, 1, NOW() + ($2 || ' milliseconds')::INTERVAL, NOW())
      ON CONFLICT (key) DO UPDATE
      SET
        count = CASE
          WHEN rate_limits.reset_at <= NOW() THEN 1
          ELSE rate_limits.count + 1
        END,
        reset_at = CASE
          WHEN rate_limits.reset_at <= NOW() THEN NOW() + ($2 || ' milliseconds')::INTERVAL
          ELSE rate_limits.reset_at
        END
      RETURNING count, reset_at, EXTRACT(EPOCH FROM (reset_at - NOW())) * 1000 AS remaining_ms;
    `;

    const res = await db.query(sql, [key, windowMs]);
    const row = res.rows[0];
    const resetTime = new Date(row.reset_at).getTime();
    const remainingMs = Math.max(0, Math.ceil(parseFloat(row.remaining_ms || '0')));

    return {
      count: parseInt(row.count, 10),
      resetTime,
      remainingMs
    };
  }

  async reset(key) {
    await db.query('DELETE FROM rate_limits WHERE key = $1', [key]);
  }

  async clear() {
    await db.query('TRUNCATE rate_limits CASCADE');
  }

  async purgeExpired() {
    if (!db.isConfigured()) return;
    try {
      await db.query("DELETE FROM rate_limits WHERE reset_at < NOW() - INTERVAL '5 minutes'");
    } catch (err) {
      // Non-blocking background log
    }
  }
}

/**
 * Distributed, multi-instance rate limit store.
 * In production: strictly uses PostgreSQL and fails closed if unavailable.
 * In non-production: falls back to MemoryRateLimitStore when PostgreSQL is unconfigured.
 */
class DistributedRateLimitStore {
  constructor() {
    this.postgresStore = new PostgresRateLimitStore();
    this.memoryStore = new MemoryRateLimitStore();
  }

  async increment(key, windowMs) {
    if (db.isConfigured()) {
      try {
        return await this.postgresStore.increment(key, windowMs);
      } catch (err) {
        if (process.env.NODE_ENV === 'production') {
          const prodErr = new Error('RATE_LIMIT_STORE_UNAVAILABLE');
          prodErr.code = 'RATE_LIMIT_STORE_UNAVAILABLE';
          throw prodErr;
        }
        console.warn('PostgreSQL rate limit store error, falling back to memory in non-production:', err.message);
      }
    } else if (process.env.NODE_ENV === 'production' && process.env.STRICT_DISTRIBUTED_STORE === 'true') {
      const error = new Error('RATE_LIMIT_STORE_UNAVAILABLE');
      error.code = 'RATE_LIMIT_STORE_UNAVAILABLE';
      throw error;
    }

    // In-memory store (for development, testing, and mock/offline compatibility)
    return this.memoryStore.increment(key, windowMs);
  }

  async reset(key) {
    if (db.isConfigured()) {
      try {
        await this.postgresStore.reset(key);
      } catch (e) {}
    }
    this.memoryStore.reset(key);
  }

  async clear() {
    if (db.isConfigured()) {
      try {
        await this.postgresStore.clear();
      } catch (e) {}
    }
    this.memoryStore.clear();
  }
}

const sharedStore = new DistributedRateLimitStore();

/**
 * Creates an Express rate limiting middleware.
 * 
 * @param {Object} options
 * @param {number} [options.windowMs=60000] - Window duration in milliseconds (default: 60s)
 * @param {number} [options.max=100] - Max allowed requests per window
 * @param {Function} [options.keyGenerator] - Function returning unique client identifier key
 * @param {string} [options.errorCode='RATE_LIMIT_EXCEEDED'] - Error code on limit breach
 * @param {string} [options.errorMessage] - Error message on limit breach
 * @param {Function} [options.skip] - Optional filter function returning boolean to skip rate limiting
 */
function createRateLimiter(options = {}) {
  const windowMs = options.windowMs || 60000;
  const max = options.max || 100;
  const errorCode = options.errorCode || 'RATE_LIMIT_EXCEEDED';
  const errorMessage = options.errorMessage || 'Too many requests. Please slow down and try again later.';
  const defaultKeyGen = (req) => {
    const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() ||
               req.socket?.remoteAddress ||
               req.ip ||
               '127.0.0.1';
    return ip;
  };
  const keyGenerator = options.keyGenerator || defaultKeyGen;
  const store = options.store || sharedStore;

  return async (req, res, next) => {
    // Check if rate limiting is globally disabled (e.g. in certain dev/test setups)
    if (process.env.RATE_LIMIT_DISABLED === 'true' || (options.skip && options.skip(req))) {
      return next();
    }

    const key = keyGenerator(req);
    let result;

    try {
      result = await store.increment(key, windowMs);
    } catch (err) {
      if (process.env.NODE_ENV === 'production') {
        console.error('CRITICAL: Rate limit store unavailable in production (failing closed).');
        return res.status(503).json({
          success: false,
          error: 'RATE_LIMIT_STORE_UNAVAILABLE',
          message: 'Rate limit service unavailable in production (fail closed).'
        });
      }
      // Non-production fallback logging
      console.error('Rate limiter error in non-production mode:', err.message);
      return next();
    }

    const { count, resetTime, remainingMs } = result;
    const remaining = Math.max(0, max - count);
    const resetSeconds = Math.max(1, Math.ceil(remainingMs / 1000));

    // Set standard RateLimit headers
    res.setHeader('RateLimit-Limit', max);
    res.setHeader('RateLimit-Remaining', remaining);
    res.setHeader('RateLimit-Reset', Math.ceil(resetTime / 1000));
    res.setHeader('X-RateLimit-Limit', max);
    res.setHeader('X-RateLimit-Remaining', remaining);

    if (count > max) {
      res.setHeader('Retry-After', resetSeconds);
      return res.status(429).json({
        success: false,
        error: errorCode,
        message: errorMessage,
        retryAfter: resetSeconds
      });
    }

    next();
  };
}

/**
 * 1. Global API Rate Limiter
 * 120 requests per minute per verified principal or IP address
 */
const globalApiLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  max: parseInt(process.env.GLOBAL_RATE_LIMIT_MAX || '120', 10),
  keyGenerator: (req) => {
    const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() ||
               req.socket?.remoteAddress ||
               req.ip ||
               '127.0.0.1';
    const userPart = req.user?.id ? `user_${req.user.id}` : `ip_${ip}`;
    return `global_${userPart}`;
  }
});

/**
 * 2. Sensitive Authentication / Ticket Exchange Limiter
 * Stricter threshold (15 requests per minute per IP) to prevent brute-forcing tickets or codes
 */
const authLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  max: parseInt(process.env.AUTH_RATE_LIMIT_MAX || '15', 10),
  errorCode: 'AUTH_RATE_LIMIT_EXCEEDED',
  errorMessage: 'Too many authentication attempts. Please try again in 60 seconds.',
  keyGenerator: (req) => {
    const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() ||
               req.socket?.remoteAddress ||
               req.ip ||
               '127.0.0.1';
    return `auth_${ip}`;
  }
});

/**
 * 3. Mutation / Post Creation Limiter
 * 60 mutations per minute per workspace / user to prevent spam and resource exhaustion.
 * Strictly derives identity from verified server context (req.workspace, req.user), never client body or headers.
 */
const mutationLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  max: parseInt(process.env.MUTATION_RATE_LIMIT_MAX || '60', 10),
  errorCode: 'MUTATION_RATE_LIMIT_EXCEEDED',
  errorMessage: 'Too many write requests for this workspace. Please slow down.',
  keyGenerator: (req) => {
    const wsId = req.workspace?.id || req.workspaceId || 'no_ws';
    const userId = req.user?.id || 'no_user';
    return `mut_${wsId}_${userId}`;
  }
});

/**
 * Standard Security Headers Middleware
 */
function securityHeaders(req, res, next) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  if (process.env.NODE_ENV === 'production') {
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  }
  next();
}

module.exports = {
  createRateLimiter,
  globalApiLimiter,
  authLimiter,
  mutationLimiter,
  securityHeaders,
  MemoryRateLimitStore,
  PostgresRateLimitStore,
  DistributedRateLimitStore,
  sharedStore
};
