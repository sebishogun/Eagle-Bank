package com.eaglebank.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Service for managing RSA keys for JWT signing
 */
@Service
@Slf4j
public class KeyManagementService {
    
    @Value("${jwt.rsa.private-key:}")
    private String privateKeyString;
    
    @Value("${jwt.rsa.public-key:}")
    private String publicKeyString;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private KeyPair keyPair;
    
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    @PostConstruct
    public void init() {
        if (privateKeyString.isEmpty() || publicKeyString.isEmpty()) {
            log.info("No RSA keys provided, generating new key pair");
            generateKeyPair();
        } else {
            log.info("Loading RSA keys from configuration");
            loadKeysFromConfiguration();
        }
    }
    
    /**
     * Generate a new RSA key pair
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, new SecureRandom());
            keyPair = keyPairGenerator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
            
            // Log the generated keys (only for development - remove in production)
            String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            log.warn("Generated new RSA key pair. Store these securely:");
            log.warn("Private Key (Base64): {}", privateKeyBase64);
            log.warn("Public Key (Base64): {}", publicKeyBase64);
            log.warn("Set these as JWT_RSA_PRIVATE_KEY and JWT_RSA_PUBLIC_KEY environment variables");
            
        } catch (Exception e) {
            log.error("Error generating RSA key pair", e);
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }
    
    /**
     * Load RSA keys from configuration
     */
    private void loadKeysFromConfiguration() {
        try {
            // Remove any whitespace and header/footer if present
            String cleanPrivateKey = cleanKeyString(privateKeyString);
            String cleanPublicKey = cleanKeyString(publicKeyString);
            
            // Decode private key
            byte[] privateKeyBytes = Base64.getDecoder().decode(cleanPrivateKey);
            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            privateKey = keyFactory.generatePrivate(privateSpec);
            
            // Decode public key
            byte[] publicKeyBytes = Base64.getDecoder().decode(cleanPublicKey);
            X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);
            publicKey = keyFactory.generatePublic(publicSpec);
            
            log.info("RSA keys loaded successfully from configuration");
            
        } catch (Exception e) {
            log.error("Error loading RSA keys from configuration, generating new ones", e);
            generateKeyPair();
        }
    }
    
    /**
     * Clean key string by removing PEM headers/footers and whitespace
     */
    private String cleanKeyString(String key) {
        return key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
    }
    
    /**
     * Get the private key for signing
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }
    
    /**
     * Get the public key for verification
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }
    
    /**
     * Check if RSA keys are configured
     */
    public boolean isRsaEnabled() {
        return privateKey != null && publicKey != null;
    }
    
    /**
     * Get the key pair for encryption/decryption
     */
    public KeyPair getKeyPair() {
        if (keyPair != null) {
            return keyPair;
        }
        return new KeyPair(publicKey, privateKey);
    }
}