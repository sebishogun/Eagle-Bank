package com.eaglebank.service;

import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Service for handling JWE (JSON Web Encryption) operations.
 * This provides an additional layer of security by encrypting the JWT tokens.
 */
@Service
@Slf4j
public class JweTokenService {
    
    @Value("${jwt.encryption.enabled:false}")
    private boolean encryptionEnabled;
    
    @Value("${jwt.encryption.algorithm:RSA-OAEP-256}")
    private String keyAlgorithm;
    
    @Value("${jwt.encryption.content-algorithm:A256GCM}")
    private String contentAlgorithm;
    
    @Autowired
    private KeyManagementService keyManagementService;
    
    private PublicKey encryptionPublicKey;
    private PrivateKey encryptionPrivateKey;
    
    @PostConstruct
    public void init() {
        if (encryptionEnabled) {
            log.info("JWE encryption is enabled. Initializing encryption keys...");
            // For now, reuse the same keys. In production, use separate encryption keys
            // TODO: Generate separate encryption key pair
            KeyPair keyPair = keyManagementService.getKeyPair();
            this.encryptionPublicKey = keyPair.getPublic();
            this.encryptionPrivateKey = keyPair.getPrivate();
            log.info("JWE encryption initialized with algorithm: {} and content encryption: {}", 
                    keyAlgorithm, contentAlgorithm);
        } else {
            log.info("JWE encryption is disabled");
        }
    }
    
    /**
     * Encrypts a JWT (JWS) to create a JWE token.
     * This implements the "sign then encrypt" pattern for maximum security.
     * 
     * @param jwt The signed JWT to encrypt
     * @return The encrypted JWE token
     * @throws JoseException if encryption fails
     */
    public String encryptToken(String jwt) throws JoseException {
        if (!encryptionEnabled) {
            // Return JWT as-is if encryption is disabled
            return jwt;
        }
        
        JsonWebEncryption jwe = new JsonWebEncryption();
        
        // Set the algorithm for key encryption
        jwe.setAlgorithmHeaderValue(mapKeyAlgorithm(keyAlgorithm));
        
        // Set the algorithm for content encryption
        jwe.setEncryptionMethodHeaderParameter(mapContentAlgorithm(contentAlgorithm));
        
        // Set the public key for encryption
        jwe.setKey(encryptionPublicKey);
        
        // Set the JWT as the payload to be encrypted
        jwe.setPayload(jwt);
        
        // Set content type to indicate nested JWT
        jwe.setContentTypeHeaderValue("JWT");
        
        // Produce the JWE compact serialization
        String encryptedToken = jwe.getCompactSerialization();
        
        log.debug("Successfully encrypted JWT to JWE");
        return encryptedToken;
    }
    
    /**
     * Decrypts a JWE token to retrieve the original JWT (JWS).
     * 
     * @param jweToken The encrypted JWE token
     * @return The decrypted JWT
     * @throws JoseException if decryption fails
     */
    public String decryptToken(String jweToken) throws JoseException {
        if (!encryptionEnabled) {
            // Return token as-is if encryption is disabled
            return jweToken;
        }
        
        // Check if this is actually a JWE token (5 parts) vs JWT (3 parts)
        String[] parts = jweToken.split("\\.");
        if (parts.length != 5) {
            // Not a JWE token, return as-is (backward compatibility)
            log.debug("Token is not JWE format (has {} parts), treating as JWT", parts.length);
            return jweToken;
        }
        
        JsonWebEncryption jwe = new JsonWebEncryption();
        
        // Set the compact serialization
        jwe.setCompactSerialization(jweToken);
        
        // Set the private key for decryption
        jwe.setKey(encryptionPrivateKey);
        
        // Get the payload (the original JWT)
        String jwt = jwe.getPayload();
        
        log.debug("Successfully decrypted JWE to JWT");
        return jwt;
    }
    
    /**
     * Checks if a token is encrypted (JWE format).
     * 
     * @param token The token to check
     * @return true if the token is JWE format, false if JWT format
     */
    public boolean isEncryptedToken(String token) {
        if (token == null) {
            return false;
        }
        // JWE tokens have 5 parts, JWT tokens have 3 parts
        return token.split("\\.").length == 5;
    }
    
    /**
     * Maps configuration string to JOSE key management algorithm.
     */
    private String mapKeyAlgorithm(String algorithm) {
        switch (algorithm) {
            case "RSA-OAEP":
                return KeyManagementAlgorithmIdentifiers.RSA_OAEP;
            case "RSA-OAEP-256":
                return KeyManagementAlgorithmIdentifiers.RSA_OAEP_256;
            case "RSA1_5":
                return KeyManagementAlgorithmIdentifiers.RSA1_5;
            default:
                log.warn("Unknown key algorithm: {}, defaulting to RSA-OAEP-256", algorithm);
                return KeyManagementAlgorithmIdentifiers.RSA_OAEP_256;
        }
    }
    
    /**
     * Maps configuration string to JOSE content encryption algorithm.
     */
    private String mapContentAlgorithm(String algorithm) {
        switch (algorithm) {
            case "A128GCM":
                return ContentEncryptionAlgorithmIdentifiers.AES_128_GCM;
            case "A192GCM":
                return ContentEncryptionAlgorithmIdentifiers.AES_192_GCM;
            case "A256GCM":
                return ContentEncryptionAlgorithmIdentifiers.AES_256_GCM;
            case "A128CBC-HS256":
                return ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256;
            case "A256CBC-HS512":
                return ContentEncryptionAlgorithmIdentifiers.AES_256_CBC_HMAC_SHA_512;
            default:
                log.warn("Unknown content encryption algorithm: {}, defaulting to A256GCM", algorithm);
                return ContentEncryptionAlgorithmIdentifiers.AES_256_GCM;
        }
    }
    
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}