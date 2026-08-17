/**
 * Error Classification and Retry Policy for Headless Publishing Engine.
 */

const ErrorCategory = {
  TRANSIENT: 'TRANSIENT',
  AMBIGUOUS: 'AMBIGUOUS',
  AUTH_FAILURE: 'AUTH_FAILURE',
  RATE_LIMIT: 'RATE_LIMIT',
  VALIDATION_FAILURE: 'VALIDATION_FAILURE',
  PLATFORM_PERMANENT: 'PLATFORM_PERMANENT',
  UNKNOWN: 'UNKNOWN'
};

class RetryPolicy {
  constructor(options = {}) {
    this.maxAttempts = options.maxAttempts || 3;
    this.baseBackoffMs = options.baseBackoffMs || 1000;
    this.maxBackoffMs = options.maxBackoffMs || 60000;
    this.jitterRatio = options.jitterRatio !== undefined ? options.jitterRatio : 0.2;
  }

  /**
   * Classify an error into a retry category.
   * @param {string|Error} error
   * @param {string} [errorCode]
   * @returns {string} ErrorCategory
   */
  classifyError(error, errorCode = null) {
    const code = (errorCode || '').toUpperCase();
    const message = (typeof error === 'string' ? error : (error?.message || '')).toUpperCase();

    // Authentication / Credential failures (NEVER retry - requires user re-auth)
    if (
      code.includes('AUTH') ||
      code.includes('TOKEN') ||
      code.includes('PERMISSION') ||
      code.includes('UNAUTHORIZED') ||
      code.includes('FORBIDDEN') ||
      code.includes('REAUTH') ||
      message.includes('TOKEN EXPIRED') ||
      message.includes('TOKEN REVOKED') ||
      message.includes('INVALID OAUTH') ||
      message.includes('OAUTH_ERROR') ||
      message.includes('REAUTH_REQUIRED') ||
      message.includes('SESSION HAS EXPIRED') ||
      message.includes('CANNOT ACCESS')
    ) {
      return ErrorCategory.AUTH_FAILURE;
    }

    // Rate limits (Retryable with backoff)
    if (
      code.includes('RATE_LIMIT') ||
      code.includes('TOO_MANY_REQUESTS') ||
      code === '429' ||
      message.includes('RATE LIMIT') ||
      message.includes('TOO MANY REQUESTS') ||
      message.includes('CALLS HAVE BEEN REDUCED')
    ) {
      return ErrorCategory.RATE_LIMIT;
    }

    // Validation / Content policy failures (NEVER retry - permanent content error)
    if (
      code.includes('VALIDATION') ||
      code.includes('INVALID_MEDIA') ||
      code.includes('CONTENT_POLICY') ||
      code.includes('MEDIA_UNSUPPORTED') ||
      code.includes('PAYLOAD_TOO_LARGE') ||
      code.includes('TEXT_TOO_LONG') ||
      message.includes('VALIDATION ERROR') ||
      message.includes('UNSUPPORTED MEDIA') ||
      message.includes('CONTENT VIOLATION') ||
      message.includes('CHARACTER LIMIT')
    ) {
      return ErrorCategory.VALIDATION_FAILURE;
    }

    // Platform permanent errors (NEVER retry)
    if (
      code.includes('ACCOUNT_DISABLED') ||
      code.includes('PAGE_NOT_FOUND') ||
      code.includes('TARGET_NOT_FOUND') ||
      code.includes('CAPABILITY_MISSING') ||
      message.includes('ACCOUNT HAS BEEN DISABLED') ||
      message.includes('PAGE NOT FOUND') ||
      message.includes('CAPABILITY NOT SUPPORTED')
    ) {
      return ErrorCategory.PLATFORM_PERMANENT;
    }

    // Ambiguous external errors (Timeouts, Socket hangups, 502/504 Bad Gateway, Network Disconnects during in-flight transmission)
    // CRITICAL: Ambiguous errors MUST NEVER be automatically treated as simple transient errors.
    if (
      code.includes('AMBIGUOUS') ||
      code.includes('ESOCKETTIMEDOUT') ||
      code.includes('504') ||
      code.includes('502') ||
      code === 'GATEWAY_TIMEOUT' ||
      code === 'BAD_GATEWAY' ||
      code === 'SOCKET_HANG_UP' ||
      code === 'CRASH_IN_FLIGHT_LEASE_EXPIRED' ||
      code === 'IN_FLIGHT_TIMEOUT' ||
      code === 'AMBIGUOUS_EXTERNAL_OUTCOME' ||
      code === 'RECONCILED_INDETERMINATE' ||
      message.includes('SOCKET HANG UP') ||
      message.includes('504') ||
      message.includes('502') ||
      message.includes('GATEWAY TIMEOUT') ||
      message.includes('BAD GATEWAY') ||
      message.includes('AMBIGUOUS') ||
      message.includes('RESPONSE TIMEOUT AFTER SEND')
    ) {
      return ErrorCategory.AMBIGUOUS;
    }

    // Transient network/server errors (Clean pre-request or safe server 503 / 500 errors)
    if (
      code.includes('TIMEOUT') ||
      code.includes('NETWORK') ||
      code.includes('ECONNRESET') ||
      code.includes('ETIMEDOUT') ||
      code.includes('ENOTFOUND') ||
      code.includes('500') ||
      code.includes('503') ||
      message.includes('NETWORK ERROR') ||
      message.includes('TIMEOUT') ||
      message.includes('SERVER TEMPORARILY UNAVAILABLE')
    ) {
      return ErrorCategory.TRANSIENT;
    }

    return ErrorCategory.UNKNOWN;
  }

  /**
   * Determine if an error category is retryable without reconciliation.
   * @param {string} category
   * @returns {boolean}
   */
  isRetryable(category) {
    return category === ErrorCategory.TRANSIENT ||
           category === ErrorCategory.RATE_LIMIT ||
           category === ErrorCategory.UNKNOWN;
  }

  /**
   * Determine if an error category requires deterministic reconciliation (ambiguous external outcome).
   * @param {string} category
   * @returns {boolean}
   */
  isAmbiguous(category) {
    return category === ErrorCategory.AMBIGUOUS;
  }

  /**
   * Calculate next attempt timestamp with exponential backoff and jitter.
   * @param {number} attemptCount - Current attempt count (0-indexed or 1-indexed)
   * @returns {Date} Next attempt timestamp
   */
  calculateNextAttempt(attemptCount) {
    const exponent = Math.max(0, attemptCount);
    const backoff = Math.min(
      this.maxBackoffMs,
      this.baseBackoffMs * Math.pow(2, exponent)
    );
    // Add jitter (e.g. ±20%)
    const jitter = backoff * this.jitterRatio * (Math.random() * 2 - 1);
    const delayMs = Math.max(this.baseBackoffMs, Math.round(backoff + jitter));
    return new Date(Date.now() + delayMs);
  }
}

module.exports = {
  ErrorCategory,
  RetryPolicy
};
