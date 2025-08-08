package com.eaglebank.security;

import com.eaglebank.service.KeyManagementService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
        
        return builder.compact();
    }
    
    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }
    
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("email", String.class);
    }
    
    public Integer getSecurityVersionFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("secVer", Integer.class);
    }
    
    public boolean validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
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
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }
}