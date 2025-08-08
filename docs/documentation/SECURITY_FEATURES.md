# Eagle Bank API Security Features

## Overview
This document describes the comprehensive security features implemented in the Eagle Bank API.

## Security Versioning System

### What is Security Versioning?
Each user has a `security_version` field (integer) that increments on critical security events. This version is embedded in JWT tokens and validated on every request to prevent token replay attacks after security changes.

### How It Works
1. **Token Generation**: When a JWT is created, the current user's security version is embedded as a claim (`secVer`)
2. **Request Validation**: On each API request, the token's security version is compared with the user's current version in the database
3. **Version Mismatch**: If versions don't match, the token is rejected (403 Forbidden)
4. **Version Increment**: Security version increments when:
   - User changes password
   - Admin resets user password
   - Security breach detected

### Benefits
- Instant token invalidation without waiting for expiry
- Prevents race conditions where old tokens remain valid after password changes
- No need to maintain individual token blacklists for password changes
- Atomic operation prevents timing attacks

## Password Security

### BCrypt Hashing
- **Algorithm**: BCryptPasswordEncoder with default work factor (10 rounds)
- **Salt**: Automatically generated random salt per password
- **Storage**: Only the hashed password is stored, never plaintext
- **Verification**: Uses constant-time comparison to prevent timing attacks

### Password Strength Requirements
- Minimum 8 characters
- Must contain uppercase, lowercase, numbers, and special characters
- Entropy calculation to ensure password complexity
- Rejects common passwords and patterns
- Prevents using personal information (name, email) in password

## JWT Token Security

### Token Types
1. **Access Token**: Short-lived (15 minutes), used for API authentication
2. **Refresh Token**: Long-lived (7 days), used to obtain new access tokens

### RSA Digital Signatures (RS256)
- **Private Key**: Used by server to sign tokens
- **Public Key**: Can be shared for token verification
- **Key Size**: 2048-bit RSA keys
- **Algorithm**: RS256 (RSA with SHA-256)
- **Benefits**: 
  - Asymmetric cryptography prevents token forgery
  - Public key can be distributed for verification
  - Better than HMAC for distributed systems

## JWE (JSON Web Encryption)

### What is JWE?
JWE adds a layer of encryption on top of signed JWTs, creating nested tokens (sign-then-encrypt pattern).

### Implementation Details
- **Key Management Algorithm**: RSA-OAEP-256
- **Content Encryption Algorithm**: AES-256-GCM
- **Token Structure**: 5-part format (vs 3-part JWT)
- **Nested JWT**: Original signed JWT is encrypted as JWE payload

### JWE Token Format
```
HEADER.ENCRYPTED_KEY.INITIALIZATION_VECTOR.CIPHERTEXT.AUTHENTICATION_TAG
```

### Benefits of JWE
1. **Confidentiality**: Token contents hidden from clients
2. **No Information Leakage**: User ID, email, roles not visible in token
3. **Defense in Depth**: Even if TLS compromised, tokens remain encrypted
4. **Compliance**: Meets strict data protection requirements (GDPR, PCI-DSS)

### Configuration
```yaml
jwt:
  encryption:
    enabled: true                    # Enable/disable JWE
    algorithm: RSA-OAEP-256          # Key encryption algorithm
    content-algorithm: A256GCM       # Content encryption algorithm
```

## Token Blacklisting

### Redis-Based Blacklist
- **Storage**: Redis with automatic TTL matching token expiry
- **Operations**:
  - Blacklist individual tokens on logout
  - Track all user tokens for bulk revocation
  - Automatic cleanup when tokens expire

### When Tokens Are Blacklisted
1. User explicitly logs out
2. Password change (all user tokens)
3. Account deactivation
4. Security breach detection

## Session Security

### Token Expiry Strategy
- **Access Token**: 15 minutes (reduced from 24 hours)
- **Refresh Token**: 7 days
- **Rotation**: New refresh token on each refresh
- **Benefits**: Limits exposure window for compromised tokens

### Refresh Token Rotation
- Each refresh generates new refresh token
- Old refresh token immediately invalidated
- Prevents refresh token replay attacks
- Stored in Redis with user association

## API Security Headers

### CORS Configuration
- Configurable allowed origins
- Credentials support for cookies
- Restricted methods and headers

### Security Headers (via Spring Security)
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection: 1; mode=block
- Cache-Control: no-cache, no-store, must-revalidate

## Audit Trail

### Tracked Events
- Login attempts (success/failure)
- Token refresh
- Password changes
- Account modifications
- Failed authentication attempts

### Information Captured
- User ID and email
- IP address
- User agent
- Timestamp
- Action performed
- Success/failure status

## Rate Limiting

### Login Attempt Throttling
- Max 5 failed attempts per account
- Progressive delays after failures
- IP-based tracking
- Automatic unlock after timeout

## Using with Postman

### No Changes Required
The Postman collection works seamlessly with JWE tokens because:
1. **Tokens are Opaque**: Clients treat tokens as opaque strings
2. **Same Usage Pattern**: Use tokens in Authorization header as before
3. **Automatic Handling**: Server handles encryption/decryption transparently

### Token Inspection
JWE tokens cannot be decoded client-side (by design). To inspect token contents:
1. Check server logs for token claims
2. Use admin endpoints to verify token status
3. Monitor authentication responses for user info

### Updated Token Characteristics
- **Length**: JWE tokens are ~2x longer than JWT tokens
- **Format**: 5 parts separated by dots (vs 3 for JWT)
- **Expiry**: 15 minutes (reduced from 24 hours)
- **Refresh Required**: More frequent refresh due to shorter expiry

### Environment Variables
Update Postman environment variables:
```javascript
// Increased max length for JWE tokens
pm.environment.set("auth_token", jsonData.token); // Handles longer JWE tokens
pm.environment.set("refresh_token", jsonData.refreshToken);
```

## Testing Security Features

### Test Scripts Provided
1. `test-jwe.sh` - Verify JWE encryption is working
2. `test-race-condition.sh` - Test security version validation
3. `test-token-blacklist.sh` - Verify logout blacklisting

### Manual Testing
```bash
# 1. Login to get JWE token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@eaglebank.com", "password": "Admin123!"}'

# 2. Token will have 5 parts if JWE is enabled
# Example: xxx.yyy.zzz.aaa.bbb (5 parts = JWE)

# 3. Use token normally
curl -X GET http://localhost:8080/api/v1/users/{userId} \
  -H "Authorization: Bearer {token}"
```

## Security Best Practices Implemented

1. **Defense in Depth**: Multiple layers of security
2. **Least Privilege**: Role-based access control
3. **Fail Secure**: Deny by default on errors
4. **Audit Everything**: Comprehensive logging
5. **Encrypt at Rest**: Redis encryption, database encryption
6. **Encrypt in Transit**: TLS/HTTPS, JWE tokens
7. **Token Hygiene**: Short expiry, rotation, blacklisting
8. **Password Hygiene**: Strong hashing, complexity requirements
9. **Version Control**: Security versioning for instant revocation
10. **Rate Limiting**: Prevent brute force attacks

## Compliance Considerations

### GDPR Compliance
- Token encryption prevents data leakage
- Audit trail for accountability
- Secure password storage
- Data minimization in tokens

### PCI-DSS Alignment
- Strong cryptography (RSA-2048, AES-256)
- Access control and authentication
- Regular security testing capabilities
- Audit trails and monitoring

### OWASP Top 10 Mitigations
- A02 Cryptographic Failures: RSA signing, JWE encryption, BCrypt
- A03 Injection: Parameterized queries, input validation
- A07 Identification and Authentication Failures: MFA ready, strong passwords
- A08 Software and Data Integrity Failures: Token signing, version control
- A09 Security Logging: Comprehensive audit trail
- A10 Server-Side Request Forgery: Not applicable (no external requests from user input)

## Future Enhancements

### Planned Features
1. Multi-factor authentication (MFA)
2. Biometric authentication support
3. Hardware security module (HSM) integration
4. Certificate-based authentication
5. OAuth 2.0 / OpenID Connect support

### Monitoring Improvements
1. Real-time security dashboards
2. Anomaly detection with ML
3. Automated threat response
4. Security metrics and KPIs

## Configuration Reference

### Application Properties
```yaml
# JWT Configuration
jwt:
  secret: ${JWT_SECRET}                          # HMAC secret (fallback)
  expiration: 900000                             # 15 minutes
  refresh:
    expiration: 604800000                        # 7 days
  rsa:
    private-key: ${JWT_RSA_PRIVATE_KEY}         # RSA private key
    public-key: ${JWT_RSA_PUBLIC_KEY}           # RSA public key
  encryption:
    enabled: true                                # Enable JWE
    algorithm: RSA-OAEP-256                     # Key encryption
    content-algorithm: A256GCM                  # Content encryption

# Redis Configuration
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

### Environment Variables
```bash
# Required for production
JWT_SECRET=<long-random-string>
JWT_RSA_PRIVATE_KEY=<base64-encoded-private-key>
JWT_RSA_PUBLIC_KEY=<base64-encoded-public-key>

# Optional - Enable JWE encryption (default: false)
# Set to true to encrypt JWT tokens (creates JWE format)
JWT_ENCRYPTION_ENABLED=false

# Redis configuration
REDIS_PASSWORD=<redis-password>
```

## Security Contacts

For security issues or questions:
- Create an issue in the repository
- Mark security-related issues as confidential
- For critical vulnerabilities, contact the security team directly

## API Version

### v1.0.0 (Current - Only Version)
All endpoints use `/api/v1/` prefix. This is the only API version available.

### Security Features Included in v1.0.0
- JWT authentication with RSA signing (RS256)
- Optional JWE token encryption (disabled by default, set JWT_ENCRYPTION_ENABLED=true to enable)
- BCrypt password hashing with automatic salting
- Role-based access control (USER, ADMIN)
- Redis-based token blacklisting
- Security versioning for instant token invalidation
- Reduced token expiry to 15 minutes
- Refresh token rotation (7-day expiry)
- Enhanced audit logging
- Password strength validation
- Rate limiting on login attempts