const crypto = require('crypto');

/**
 * Production-grade JWT / OIDC Cryptographic Authentication Engine.
 * 
 * Supports:
 * - HS256 (HMAC-SHA256 symmetric signing & verification)
 * - RS256 (RSA-SHA256 asymmetric public key verification)
 * - Strict Base64URL encoding/decoding
 * - Cryptographic constant-time signature verification (prevents timing attacks)
 * - Algorithm confusion prevention & 'none' algorithm rejection
 * - Standard claims validation (exp, nbf, iat, iss, aud, sub)
 * - Clock skew tolerance
 * - Fail-closed behavior on missing or invalid configuration
 */

const SUPPORTED_ALGORITHMS = ['HS256', 'RS256'];
const DEFAULT_CLOCK_SKEW_SECONDS = 60;

/**
 * Base64URL encode a Buffer or string.
 * @param {Buffer|string} input
 * @returns {string}
 */
function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  return buf.toString('base64url');
}

/**
 * Base64URL decode to string or Buffer.
 * @param {string} input
 * @param {string} [encoding='utf8']
 * @returns {string|Buffer}
 */
function base64UrlDecode(input, encoding = 'utf8') {
  const buf = Buffer.from(input, 'base64url');
  return encoding ? buf.toString(encoding) : buf;
}

/**
 * Checks if JWT verification is configured in the current environment.
 * @param {Object} [options]
 * @returns {boolean}
 */
function isConfigured(options = {}) {
  const algorithm = (options.algorithm || process.env.JWT_ALGORITHM || 'HS256').toUpperCase();
  if (algorithm === 'HS256') {
    return Boolean(options.secret || process.env.JWT_SECRET);
  }
  if (algorithm === 'RS256') {
    return Boolean(options.publicKey || process.env.JWT_PUBLIC_KEY);
  }
  return false;
}

/**
 * Signs a payload into a valid cryptographic JWT token.
 * 
 * @param {Object} payload - Token claims (sub, email, etc.)
 * @param {Object} [options]
 * @param {string} [options.secret] - Symmetric secret (HS256)
 * @param {string} [options.privateKey] - PEM Private key (RS256)
 * @param {string} [options.algorithm='HS256'] - 'HS256' | 'RS256'
 * @param {number|string} [options.expiresIn=3600] - Expiration in seconds
 * @param {string} [options.issuer] - Token issuer
 * @param {string} [options.audience] - Token audience
 * @param {string} [options.subject] - Token subject (sub)
 * @returns {string} Serialized JWT string
 */
function signToken(payload, options = {}) {
  const algorithm = (options.algorithm || process.env.JWT_ALGORITHM || 'HS256').toUpperCase();
  if (!SUPPORTED_ALGORITHMS.includes(algorithm)) {
    throw new Error(`Unsupported signing algorithm: ${algorithm}`);
  }

  const now = Math.floor(Date.now() / 1000);
  let expSeconds = 3600;
  if (typeof options.expiresIn === 'number') {
    expSeconds = options.expiresIn;
  } else if (typeof options.expiresIn === 'string') {
    const match = options.expiresIn.match(/^(\d+)([smhd])$/);
    if (match) {
      const num = parseInt(match[1], 10);
      const unit = match[2];
      expSeconds = unit === 's' ? num : unit === 'm' ? num * 60 : unit === 'h' ? num * 3600 : num * 86400;
    }
  }

  const header = {
    alg: algorithm,
    typ: 'JWT'
  };

  const claims = {
    ...payload,
    iat: payload.iat !== undefined ? payload.iat : now,
    exp: payload.exp !== undefined ? payload.exp : (now + expSeconds)
  };

  if (options.issuer || process.env.JWT_ISSUER) {
    claims.iss = options.issuer || process.env.JWT_ISSUER;
  }
  if (options.audience || process.env.JWT_AUDIENCE) {
    claims.aud = options.audience || process.env.JWT_AUDIENCE;
  }
  if (options.subject) {
    claims.sub = options.subject;
  } else if (!claims.sub && (payload.userId || payload.user_id)) {
    claims.sub = payload.userId || payload.user_id;
  }

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(claims));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  let signatureBase64Url;
  if (algorithm === 'HS256') {
    const secret = options.secret || process.env.JWT_SECRET;
    if (!secret) {
      throw new Error('JWT_SECRET is required for HS256 signing');
    }
    signatureBase64Url = crypto
      .createHmac('sha256', secret)
      .update(signingInput)
      .digest('base64url');
  } else if (algorithm === 'RS256') {
    const privateKey = options.privateKey || process.env.JWT_PRIVATE_KEY;
    if (!privateKey) {
      throw new Error('JWT_PRIVATE_KEY is required for RS256 signing');
    }
    const signer = crypto.createSign('RSA-SHA256');
    signer.update(signingInput);
    signatureBase64Url = signer.sign(privateKey, 'base64url');
  }

  return `${signingInput}.${signatureBase64Url}`;
}

/**
 * Cryptographically verifies and decodes a JWT token.
 * 
 * @param {string} token - Raw JWT string from Authorization Bearer header
 * @param {Object} [options]
 * @param {string} [options.algorithm] - Expected algorithm override (default: process.env.JWT_ALGORITHM or 'HS256')
 * @param {string} [options.secret] - Optional secret override (for HS256)
 * @param {string} [options.publicKey] - Optional public key override (for RS256)
 * @param {string} [options.issuer] - Expected issuer override
 * @param {string} [options.audience] - Expected audience override
 * @param {number} [options.clockSkew] - Clock skew tolerance in seconds (default 60s)
 * @returns {{ valid: boolean, claims?: Object, header?: Object, error?: string, message?: string }}
 */
function verifyToken(token, options = {}) {
  if (!token || typeof token !== 'string') {
    return {
      valid: false,
      error: 'INVALID_TOKEN_FORMAT',
      message: 'Token must be a non-empty string.'
    };
  }

  const parts = token.trim().split('.');
  if (parts.length !== 3) {
    return {
      valid: false,
      error: 'MALFORMED_TOKEN',
      message: 'JWT token format must contain exactly 3 dot-separated segments.'
    };
  }

  const [encodedHeader, encodedPayload, signaturePart] = parts;
  if (!encodedHeader || !encodedPayload || (signaturePart === undefined)) {
    return {
      valid: false,
      error: 'MALFORMED_TOKEN',
      message: 'JWT token segments cannot be empty.'
    };
  }

  // 1. Decode and parse Header
  let header;
  try {
    const headerJson = base64UrlDecode(encodedHeader, 'utf8');
    header = JSON.parse(headerJson);
  } catch (e) {
    return {
      valid: false,
      error: 'MALFORMED_HEADER',
      message: 'Failed to decode or parse JWT header JSON.'
    };
  }

  if (!header || typeof header !== 'object' || Array.isArray(header)) {
    return {
      valid: false,
      error: 'MALFORMED_HEADER',
      message: 'JWT header must be a valid JSON object.'
    };
  }

  // 2. Algorithm Verification & Protection against 'none' attack & Algorithm Confusion
  const alg = header.alg;
  if (!alg || typeof alg !== 'string') {
    return {
      valid: false,
      error: 'MISSING_ALGORITHM',
      message: 'JWT header missing algorithm parameter.'
    };
  }

  const tokenAlg = alg.toUpperCase();
  if (tokenAlg === 'NONE') {
    return {
      valid: false,
      error: 'UNSECURED_TOKEN_REJECTED',
      message: 'Unsecured JWT (alg: none) is strictly prohibited.'
    };
  }

  if (!SUPPORTED_ALGORITHMS.includes(tokenAlg)) {
    return {
      valid: false,
      error: 'UNSUPPORTED_ALGORITHM',
      message: `Algorithm '${alg}' is not supported. Allowed: ${SUPPORTED_ALGORITHMS.join(', ')}.`
    };
  }

  // Enforce configured algorithm (Must strictly match expected algorithm)
  const configuredAlg = (options.algorithm || process.env.JWT_ALGORITHM || 'HS256').toUpperCase();
  if (!SUPPORTED_ALGORITHMS.includes(configuredAlg)) {
    return {
      valid: false,
      error: 'INVALID_CONFIGURED_ALGORITHM',
      message: `Configured algorithm '${configuredAlg}' is not supported.`
    };
  }

  if (tokenAlg !== configuredAlg) {
    return {
      valid: false,
      error: 'ALGORITHM_MISMATCH',
      message: `Algorithm mismatch: token specifies '${alg}' but configured algorithm is '${configuredAlg}'.`
    };
  }

  // 3. Cryptographic Signature Verification
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  if (configuredAlg === 'HS256') {
    const secret = options.secret || process.env.JWT_SECRET;
    if (!secret) {
      return {
        valid: false,
        error: 'AUTH_CONFIG_MISSING',
        message: 'JWT verification secret is not configured on the server.'
      };
    }

    if (!signaturePart) {
      return {
        valid: false,
        error: 'INVALID_SIGNATURE',
        message: 'Missing cryptographic signature.'
      };
    }

    const expectedSignature = crypto
      .createHmac('sha256', secret)
      .update(signingInput)
      .digest('base64url');

    const expectedBuf = Buffer.from(expectedSignature, 'utf8');
    const providedBuf = Buffer.from(signaturePart, 'utf8');

    if (expectedBuf.length !== providedBuf.length || !crypto.timingSafeEqual(expectedBuf, providedBuf)) {
      return {
        valid: false,
        error: 'INVALID_SIGNATURE',
        message: 'Cryptographic signature verification failed.'
      };
    }
  } else if (configuredAlg === 'RS256') {
    const publicKey = options.publicKey || process.env.JWT_PUBLIC_KEY;
    if (!publicKey) {
      return {
        valid: false,
        error: 'AUTH_CONFIG_MISSING',
        message: 'JWT verification public key is not configured on the server.'
      };
    }

    if (!signaturePart) {
      return {
        valid: false,
        error: 'INVALID_SIGNATURE',
        message: 'Missing cryptographic signature.'
      };
    }

    try {
      const verifier = crypto.createVerify('RSA-SHA256');
      verifier.update(signingInput);
      const signatureBuf = Buffer.from(signaturePart, 'base64url');
      const isValid = verifier.verify(publicKey, signatureBuf);
      if (!isValid) {
        return {
          valid: false,
          error: 'INVALID_SIGNATURE',
          message: 'Cryptographic asymmetric signature verification failed.'
        };
      }
    } catch (err) {
      return {
        valid: false,
        error: 'SIGNATURE_VERIFICATION_ERROR',
        message: 'Error executing cryptographic signature verification.'
      };
    }
  }

  // 4. Decode and parse Payload
  let payload;
  try {
    const payloadJson = base64UrlDecode(encodedPayload, 'utf8');
    payload = JSON.parse(payloadJson);
  } catch (e) {
    return {
      valid: false,
      error: 'MALFORMED_PAYLOAD',
      message: 'Failed to decode or parse JWT payload JSON.'
    };
  }

  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    return {
      valid: false,
      error: 'MALFORMED_PAYLOAD',
      message: 'JWT payload must be a valid JSON object.'
    };
  }

  // 5. Standard Claims Verification
  const now = Math.floor(Date.now() / 1000);
  const clockSkew = options.clockSkew !== undefined ? options.clockSkew : DEFAULT_CLOCK_SKEW_SECONDS;

  // Expiration (exp)
  if (payload.exp === undefined) {
    return {
      valid: false,
      error: 'MISSING_EXP_CLAIM',
      message: 'Token missing required expiration (exp) claim.'
    };
  }

  if (typeof payload.exp !== 'number') {
    return {
      valid: false,
      error: 'INVALID_EXP_CLAIM',
      message: 'Expiration claim (exp) must be a numeric timestamp.'
    };
  }

  if (now > payload.exp + clockSkew) {
    return {
      valid: false,
      error: 'TOKEN_EXPIRED',
      message: 'The authentication token has expired.'
    };
  }

  // Not Before (nbf)
  if (payload.nbf !== undefined) {
    if (typeof payload.nbf !== 'number') {
      return {
        valid: false,
        error: 'INVALID_NBF_CLAIM',
        message: 'Not-before claim (nbf) must be a numeric timestamp.'
      };
    }
    if (now < payload.nbf - clockSkew) {
      return {
        valid: false,
        error: 'TOKEN_NOT_ACTIVE',
        message: 'The authentication token is not active yet.'
      };
    }
  }

  // Issued At (iat)
  if (payload.iat !== undefined) {
    if (typeof payload.iat !== 'number') {
      return {
        valid: false,
        error: 'INVALID_IAT_CLAIM',
        message: 'Issued-at claim (iat) must be a numeric timestamp.'
      };
    }
    if (payload.iat > now + clockSkew) {
      return {
        valid: false,
        error: 'TOKEN_FUTURE_IAT',
        message: 'The authentication token was issued in the future.'
      };
    }
  }

  // Issuer (iss)
  const expectedIssuer = options.issuer !== undefined ? options.issuer : process.env.JWT_ISSUER;
  if (expectedIssuer) {
    if (!payload.iss || payload.iss !== expectedIssuer) {
      return {
        valid: false,
        error: 'INVALID_ISSUER',
        message: `Token issuer does not match expected issuer.`
      };
    }
  }

  // Audience (aud)
  const expectedAudience = options.audience !== undefined ? options.audience : process.env.JWT_AUDIENCE;
  if (expectedAudience) {
    let audMatches = false;
    if (Array.isArray(payload.aud)) {
      audMatches = payload.aud.includes(expectedAudience);
    } else if (typeof payload.aud === 'string') {
      audMatches = payload.aud === expectedAudience;
    }
    if (!audMatches) {
      return {
        valid: false,
        error: 'INVALID_AUDIENCE',
        message: 'Token audience does not match expected audience.'
      };
    }
  }

  // Subject / Identity (sub)
  const subject = payload.sub;
  if (!subject || typeof subject !== 'string' || subject.trim() === '') {
    return {
      valid: false,
      error: 'MISSING_SUBJECT',
      message: 'Token missing required subject (sub) claim.'
    };
  }

  return {
    valid: true,
    claims: payload,
    header: header
  };
}

module.exports = {
  SUPPORTED_ALGORITHMS,
  DEFAULT_CLOCK_SKEW_SECONDS,
  base64UrlEncode,
  base64UrlDecode,
  isConfigured,
  signToken,
  verifyToken
};
