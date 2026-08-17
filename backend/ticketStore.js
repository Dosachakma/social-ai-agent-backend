const crypto = require('crypto');
const db = require('./db/pool');
const cryptoService = require('./services/cryptoService');

/**
 * Distributed, Production-Grade Single-Use OAuth Ticket Store.
 * 
 * Supports:
 * - Distributed PostgreSQL storage (`oauth_tickets` table) for horizontally scaled multi-instance deployments.
 * - In-memory fallback for local development, offline mode, and fast isolated unit testing.
 * - Cryptographically random 32-byte (64 hex characters) single-use authorization tickets.
 * - One-way SHA-256 hashing before storing in PostgreSQL (tickets are never stored in raw plaintext).
 * - Atomic database consumption (`UPDATE ... WHERE ticket_hash = $1 AND consumed_at IS NULL AND expires_at > NOW()`)
 *   preventing race conditions and distributed replay attacks.
 * - AES-256-GCM encryption of sensitive tokens inside `session_data`.
 * - Strict 60-second default TTL.
 * - Automatic background purging of expired tickets.
 */
class TicketStore {
  constructor(defaultTtlSeconds = 60) {
    this.defaultTtlSeconds = defaultTtlSeconds;
    this.defaultTtlMs = defaultTtlSeconds * 1000;
    this.memoryTickets = new Map();

    // Periodic cleanup of expired tickets every 30 seconds
    this.cleanupInterval = setInterval(() => {
      this.purgeExpired().catch(() => {});
    }, 30000);

    if (this.cleanupInterval && typeof this.cleanupInterval.unref === 'function') {
      this.cleanupInterval.unref();
    }
  }

  get tickets() {
    return this.memoryTickets;
  }

  /**
   * Helper: Hashes a ticket token using SHA-256.
   * @param {string} ticket - Raw 64-char hex ticket string
   * @returns {string} SHA-256 hex digest
   */
  hashTicket(ticket) {
    return crypto.createHash('sha256').update(ticket).digest('hex');
  }

  /**
   * Creates and stores a new single-use authorization ticket.
   * 
   * @param {Object} data - Contains accessToken, state, accountMetadata, workspaceId, userId, etc.
   * @param {number} [ttlSeconds] - Custom TTL override in seconds (defaults to 60)
   * @returns {Promise<string>} The generated secure 64-character hex ticket string.
   */
  async createTicket(data, ttlSeconds = null) {
    const effectiveTtlSeconds = (typeof ttlSeconds === 'number')
      ? ttlSeconds
      : this.defaultTtlSeconds;

    const rawTicket = crypto.randomBytes(32).toString('hex');
    const ticketHash = this.hashTicket(rawTicket);
    const now = Date.now();
    const expiresAt = new Date(now + effectiveTtlSeconds * 1000);

    // Securely encrypt sensitive access token before saving in session data
    let encryptedAccessToken = null;
    if (data.accessToken && typeof data.accessToken === 'string') {
      try {
        encryptedAccessToken = cryptoService.encrypt(data.accessToken);
      } catch (err) {
        encryptedAccessToken = null;
      }
    }

    const sessionPayload = {
      state: data.state || null,
      accountMetadata: data.accountMetadata || null,
      encryptedAccessToken: encryptedAccessToken,
      workspaceId: data.workspaceId || null,
      userId: data.userId || null,
      createdAt: now,
      expiresAt: expiresAt.getTime()
    };

    const isProduction = process.env.NODE_ENV === 'production';

    // In production, PostgreSQL is mandatory for distributed single-use tickets
    if (isProduction && !db.isConfigured()) {
      const err = new Error('OAUTH_TICKET_STORE_UNAVAILABLE');
      err.code = 'OAUTH_TICKET_STORE_UNAVAILABLE';
      throw err;
    }

    if (db.isConfigured()) {
      try {
        const queryText = `
          INSERT INTO oauth_tickets (
            ticket_hash,
            workspace_id,
            user_id,
            session_data,
            expires_at,
            created_at
          ) VALUES ($1, $2, $3, $4::jsonb, $5, NOW())
          RETURNING id;
        `;

        await db.query(queryText, [
          ticketHash,
          data.workspaceId || null,
          data.userId || null,
          JSON.stringify(sessionPayload),
          expiresAt
        ]);

        return rawTicket;
      } catch (dbErr) {
        if (isProduction) {
          const err = new Error('OAUTH_TICKET_STORE_UNAVAILABLE');
          err.code = 'OAUTH_TICKET_STORE_UNAVAILABLE';
          throw err;
        }
        // In case of transient DB failure in non-production, fallback to memory store
        console.warn('Database error storing OAuth ticket, falling back to memory store:', dbErr.message);
      }
    }

    // In-memory fallback (strictly non-production only)
    this.memoryTickets.set(ticketHash, {
      ...sessionPayload,
      ticketHash,
      rawTicket,
      expiresAt: expiresAt.getTime(),
      consumedAt: null
    });

    return rawTicket;
  }

  /**
   * Consumes and burns a ticket immediately to enforce single-use.
   * Atomically marks the ticket as consumed in PostgreSQL or memory.
   * 
   * @param {string} ticket - The raw 64-character ticket token.
   * @param {string} [state] - The OAuth state to verify against (CSRF mitigation).
   * @returns {Promise<{ success: boolean, data?: Object, error?: string }>}
   */
  async consumeTicket(ticket, state) {
    if (!ticket || typeof ticket !== 'string' || ticket.trim() === '') {
      return { success: false, error: 'INVALID_TICKET_FORMAT' };
    }

    const cleanTicket = ticket.trim();
    const ticketHash = this.hashTicket(cleanTicket);
    const isProduction = process.env.NODE_ENV === 'production';

    // In production, PostgreSQL is mandatory for distributed single-use tickets
    if (isProduction && !db.isConfigured()) {
      return { success: false, error: 'OAUTH_TICKET_STORE_UNAVAILABLE' };
    }

    if (db.isConfigured()) {
      try {
        // Atomic consumption in a single SQL statement:
        // Updates consumed_at ONLY if ticket exists, has not been consumed, and is not expired.
        const atomicUpdateQuery = `
          UPDATE oauth_tickets
          SET consumed_at = NOW()
          WHERE ticket_hash = $1
            AND consumed_at IS NULL
            AND expires_at > NOW()
          RETURNING session_data, expires_at, consumed_at;
        `;

        const result = await db.query(atomicUpdateQuery, [ticketHash]);

        if (result.rows.length > 0) {
          const row = result.rows[0];
          const sessionData = row.session_data || {};

          // Verify state match if state was supplied in session
          if (state && sessionData.state && sessionData.state !== state) {
            return { success: false, error: 'STATE_MISMATCH' };
          }

          return {
            success: true,
            data: {
              accountMetadata: sessionData.accountMetadata || null,
              workspaceId: sessionData.workspaceId || null,
              userId: sessionData.userId || null,
              createdAt: sessionData.createdAt || null
            }
          };
        }

        // If no row was updated, inspect why to provide descriptive security error code
        const checkQuery = `
          SELECT consumed_at, expires_at, session_data
          FROM oauth_tickets
          WHERE ticket_hash = $1;
        `;
        const checkResult = await db.query(checkQuery, [ticketHash]);

        if (checkResult.rows.length === 0) {
          return { success: false, error: 'TICKET_NOT_FOUND' };
        }

        const existingRow = checkResult.rows[0];
        if (existingRow.consumed_at) {
          return { success: false, error: 'TICKET_NOT_FOUND' }; // Burned ticket acts as not found / already used
        }

        if (new Date() > new Date(existingRow.expires_at)) {
          return { success: false, error: 'TICKET_EXPIRED' };
        }

        return { success: false, error: 'TICKET_NOT_FOUND' };
      } catch (dbErr) {
        if (isProduction) {
          return { success: false, error: 'OAUTH_TICKET_STORE_UNAVAILABLE' };
        }
        console.warn('Database error consuming OAuth ticket, checking memory fallback:', dbErr.message);
      }
    }

    // In-memory atomic consumption fallback (strictly non-production only)
    const entry = this.memoryTickets.get(ticketHash);

    if (!entry) {
      return { success: false, error: 'TICKET_NOT_FOUND' };
    }

    // Burn / delete ticket from memory store immediately
    this.memoryTickets.delete(ticketHash);

    // Check consumption and expiration
    if (entry.consumedAt) {
      return { success: false, error: 'TICKET_NOT_FOUND' };
    }

    if (Date.now() > entry.expiresAt) {
      return { success: false, error: 'TICKET_EXPIRED' };
    }

    // Verify state match
    if (state && entry.state && entry.state !== state) {
      return { success: false, error: 'STATE_MISMATCH' };
    }

    // Return sanitized metadata only — NEVER return raw or encrypted tokens
    return {
      success: true,
      data: {
        accountMetadata: entry.accountMetadata || null,
        workspaceId: entry.workspaceId || null,
        userId: entry.userId || null,
        createdAt: entry.createdAt || null
      }
    };
  }

  /**
   * Purges expired and aged consumed tickets from storage.
   */
  async purgeExpired() {
    const now = Date.now();

    // 1. Purge memory store
    for (const [hash, entry] of this.memoryTickets.entries()) {
      if (now > entry.expiresAt || (entry.consumedAt && now - entry.consumedAt > 3600000)) {
        this.memoryTickets.delete(hash);
      }
    }

    // 2. Purge PostgreSQL store if configured
    if (db.isConfigured()) {
      try {
        const purgeQuery = `
          DELETE FROM oauth_tickets
          WHERE expires_at < NOW()
             OR (consumed_at IS NOT NULL AND consumed_at < NOW() - INTERVAL '1 hour');
        `;
        await db.query(purgeQuery);
      } catch (err) {
        // Non-blocking background log
      }
    }
  }

  /**
   * Clears all tickets (primarily for test suite tear-down).
   */
  async clear() {
    this.memoryTickets.clear();
    if (db.isConfigured()) {
      try {
        await db.query('TRUNCATE oauth_tickets CASCADE;');
      } catch (err) {
        // Non-blocking
      }
    }
  }
}

module.exports = new TicketStore(60);
