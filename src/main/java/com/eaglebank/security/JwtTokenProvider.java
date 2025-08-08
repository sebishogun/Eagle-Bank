package com.eaglebank.security;

import com.eaglebank.service.JweTokenService;
import com.eaglebank.service.KeyManagementService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private long expiration;
    
    @Autowired(required = false)
    private KeyManagementService keyManagementService;
    
    @Autowired(required = false)
    private JweTokenService jweTokenService;
    
    private SecretKey hmacKey;
    private Key signingKey;
    private JwtParser jwtParser;
    private boolean useRsa = false;
    
    @PostConstruct
    public void init() {
        // Check if RSA keys are available
        if (keyManagementService != null && keyManagementService.isRsaEnabled()) {
            log.info("Using RSA (RS256) for JWT signing");
            this.signingKey = keyManagementService.getPrivateKey();
            this.jwtParser = Jwts.parser()
                    .verifyWith(keyManagementService.getPublicKey())
                    .build();
            this.useRsa = true;
        } else {
            log.info("Using HMAC (HS256) for JWT signing");
            this.hmacKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.signingKey = this.hmacKey;
            this.jwtParser = Jwts.parser()
                    .verifyWith(hmacKey)
                    .build();
            this.useRsa = false;
        }
    }
    
    public String generateToken(UUID userId, String email, Integer securityVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        JwtBuilder builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("secVer", securityVersion != null ? securityVersion : 0)
                .issuedAt(now)
                .expiration(expiryDate);
        
        // Sign with appropriate key (RSA or HMAC)
        if (useRsa) {
            builder.signWith(signingKey, SignatureAlgorithm.RS256);
        } else {
            builder.signWith(signingKey);
        }
        
        String jwt = builder.compact();
        
        // Encrypt the JWT if JWE is enabled
        if (jweTokenService != null && jweTokenService.isEncryptionEnabled()) {
            try {
                return jweTokenService.encryptToken(jwt);
            } catch (JoseException e) {
                log.error("Failed to encrypt JWT to JWE, returning unencrypted JWT", e);
                return jwt;
            }
        }
        
        return jwt;
    }
    
    public UUID getUserIdFromToken(String token) {
        String jwt = decryptIfNeeded(token);
        Claims claims = getClaimsFromToken(jwt);
        return UUID.fromString(claims.getSubject());
    }
    
    public String getEmailFromToken(String token) {
        String jwt = decryptIfNeeded(token);
        Claims claims = getClaimsFromToken(jwt);
        return claims.get("email", String.class);
    }
    
    public Integer getSecurityVersionFromToken(String token) {
        String jwt = decryptIfNeeded(token);
        Claims claims = getClaimsFromToken(jwt);
        return claims.get("secVer", Integer.class);
    }
    
    public boolean validateToken(String token) {
        try {
            String jwt = decryptIfNeeded(token);
            jwtParser.parseSignedClaims(jwt);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        } catch (SecurityException ex) {
            log.error("JWT signature validation failed");
        }
        return false;
    }
    
    public Claims getClaimsFromToken(String token) {
        // Note: token should already be decrypted before calling this
        // This is to avoid double decryption
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Date getExpirationDateFromToken(String token) {
        String jwt = decryptIfNeeded(token);
        Claims claims = getClaimsFromToken(jwt);
        return claims.getExpiration();
    }
    
    /**
     * Decrypt the token if it's JWE format, otherwise return as-is.
     */
    private String decryptIfNeeded(String token) {
        if (jweTokenService == null || !jweTokenService.isEncryptionEnabled()) {
            return token;
        }
        
        try {
            return jweTokenService.decryptToken(token);
        } catch (JoseException e) {
            log.error("Failed to decrypt JWE token, treating as regular JWT", e);
            return token;
        }
    }
}