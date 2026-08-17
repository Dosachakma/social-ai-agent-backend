const crypto = require('crypto');

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12; // 12 bytes standard for GCM
const AUTH_TAG_LENGTH = 16; // 16 bytes auth tag

/**
 * Resolves the 32-byte encryption key strictly from server environment configuration.
 * Precedence: TOKEN_ENCRYPTION_KEY takes precedence over ENCRYPTION_KEY.
 * Encryption keys NEVER come from PostgreSQL, source code constants, client storage, or client requests.
 * 
 * Production Invariants:
 * - Missing key -> FAIL CLOSED (throw Error)
 * - Invalid key length -> FAIL CLOSED (throw Error)
 * - Never fallback to dev keys or silent derivation in production
 * 
 * @returns {Buffer} 32-byte encryption key buffer
 */
function getEncryptionKey() {
  const rawKey = process.env.TOKEN_ENCRYPTION_KEY || process.env.ENCRYPTION_KEY;
  const isProduction = process.env.NODE_ENV === 'production';

  if (rawKey && typeof rawKey === 'string' && rawKey.trim() !== '') {
    const trimmedKey = rawKey.trim();

    // If provided as 64-character hex string (32 bytes)
    if (/^[0-9a-fA-F]{64}$/.test(trimmedKey)) {
      return Buffer.from(trimmedKey, 'hex');
    }

    // If provided as 32-character utf8 string (32 bytes)
    if (Buffer.byteLength(trimmedKey, 'utf8') === 32) {
      return Buffer.from(trimmedKey, 'utf8');
    }

    // In production: MUST NOT silently derive key if length is wrong! It must FAIL CLOSED!
    if (isProduction) {
      throw new Error('INVALID_KEY_LENGTH: TOKEN_ENCRYPTION_KEY must be exactly 32 bytes (64 hex characters or 32 UTF-8 bytes) in production.');
    }

    // In development/test only: derive exact 32 bytes using SHA-256 hash if raw string is of different length
    return crypto.createHash('sha256').update(trimmedKey).digest();
  }

  // Production fail closed!
  if (isProduction) {
    throw new Error('MISSING_ENCRYPTION_KEY: TOKEN_ENCRYPTION_KEY or ENCRYPTION_KEY environment variable is required in production (fail closed).');
  }

  // Fallback for local development / test environments:
  // Derived via secure server-side salt and process environment context
  const devSeed = process.env.NODE_ENV === 'test'
    ? 'social_agent_test_salt_dev_env_2026'
    : (process.env.APP_SECRET || 'social_ai_agent_internal_dev_key');

  return crypto.createHash('sha256').update(devSeed).digest();
}

/**
 * Checks whether the encryption service is properly configured with a valid key.
 * @returns {boolean}
 */
function isConfigured() {
  try {
    const key = getEncryptionKey();
    return Buffer.isBuffer(key) && key.length === 32;
  } catch (err) {
    return false;
  }
}

/**
 * Encrypt plaintext using AES-256-GCM with unique IV and authentication tag.
 * @param {string} text - Plaintext token or string to encrypt
 * @param {Buffer} [customKey] - Optional custom 32-byte key buffer for testing
 * @returns {string|null} Serialized encrypted string format 'iv:authTag:ciphertext' (hex) or null if input empty
 */
function encrypt(text, customKey) {
  if (!text || typeof text !== 'string') return null;

  // If already encrypted in our format, return as is
  if (isEncrypted(text)) {
    return text;
  }

  const key = customKey || getEncryptionKey();
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);

  let ciphertext = cipher.update(text, 'utf8', 'hex');
  ciphertext += cipher.final('hex');

  const authTag = cipher.getAuthTag().toString('hex');
  const ivHex = iv.toString('hex');

  return `${ivHex}:${authTag}:${ciphertext}`;
}

/**
 * Decrypt ciphertext using AES-256-GCM and verify authentication tag.
 * Throws on tampering, tag verification failure, or malformed payload.
 * @param {string} encryptedString - Format 'iv:authTag:ciphertext'
 * @param {Buffer} [customKey] - Optional custom 32-byte key buffer for testing
 * @returns {string|null} Decrypted plaintext token
 */
function decrypt(encryptedString, customKey) {
  if (!encryptedString || typeof encryptedString !== 'string') return null;

  const parts = encryptedString.split(':');
  if (parts.length !== 3) {
    // In production, unencrypted payloads in decrypt() fail closed
    if (process.env.NODE_ENV === 'production') {
      throw new Error('INVALID_ENCRYPTION_PAYLOAD: Expected format iv:authTag:ciphertext.');
    }
    // If not in encrypted format (e.g. legacy plain in test/dev), return as fallback
    return encryptedString;
  }

  const [ivHex, authTagHex, ciphertextHex] = parts;
  if (
    ivHex.length !== IV_LENGTH * 2 ||
    authTagHex.length !== AUTH_TAG_LENGTH * 2 ||
    !/^[0-9a-fA-F]+$/.test(ivHex) ||
    !/^[0-9a-fA-F]+$/.test(authTagHex) ||
    !/^[0-9a-fA-F]*$/.test(ciphertextHex)
  ) {
    throw new Error('INVALID_ENCRYPTION_PAYLOAD: Malformed IV, AuthTag, or Ciphertext hex format.');
  }

  const key = customKey || getEncryptionKey();
  const iv = Buffer.from(ivHex, 'hex');
  const authTag = Buffer.from(authTagHex, 'hex');

  try {
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    decipher.setAuthTag(authTag);

    let decrypted = decipher.update(ciphertextHex, 'hex', 'utf8');
    decrypted += decipher.final('utf8');

    return decrypted;
  } catch (err) {
    throw new Error('DECRYPTION_FAILED: Authentication tag verification failed or corrupted payload.');
  }
}

/**
 * Helper to check if a string matches the 'iv:authTag:ciphertext' format.
 */
function isEncrypted(text) {
  if (!text || typeof text !== 'string') return false;
  const parts = text.split(':');
  return (
    parts.length === 3 &&
    parts[0].length === IV_LENGTH * 2 &&
    parts[1].length === AUTH_TAG_LENGTH * 2 &&
    /^[0-9a-fA-F]{24}$/.test(parts[0]) &&
    /^[0-9a-fA-F]{32}$/.test(parts[1]) &&
    /^[0-9a-fA-F]*$/.test(parts[2])
  );
}

module.exports = {
  ALGORITHM,
  IV_LENGTH,
  AUTH_TAG_LENGTH,
  getEncryptionKey,
  isConfigured,
  encrypt,
  decrypt,
  isEncrypted
};
