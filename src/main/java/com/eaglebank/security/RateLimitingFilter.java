package com.eaglebank.security;

import com.eaglebank.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter to prevent API abuse and ensure fair usage.
 * Uses Token Bucket algorithm via bucket4j library.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Value("${rate-limiting.authenticated.capacity:100}")
    private int authenticatedCapacity;
    
    @Value("${rate-limiting.authenticated.tokens:100}")
    private int authenticatedTokens;
    
    @Value("${rate-limiting.authenticated.period:60}")
    private int authenticatedPeriodSeconds;
    
    @Value("${rate-limiting.anonymous.capacity:20}")
    private int anonymousCapacity;
    
    @Value("${rate-limiting.anonymous.tokens:20}")
    private int anonymousTokens;
    
    @Value("${rate-limiting.anonymous.period:60}")
    private int anonymousPeriodSeconds;
    
    @Value("${rate-limiting.ip-based:true}")
    private boolean ipBasedRateLimiting;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String key = resolveKey(request);
        Bucket bucket = resolveBucket(key);
        
        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            addRateLimitHeaders(response, bucket);
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            handleRateLimitExceeded(response, bucket);
        }
    }
    
    /**
     * Resolves the key for rate limiting based on authentication status and configuration
     */
    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getPrincipal())) {
            // Use username for authenticated users
            return "user:" + authentication.getName();
        } else if (ipBasedRateLimiting) {
            // Use IP address for anonymous users
            return "ip:" + getClientIpAddress(request);
        } else {
            // Global anonymous bucket
            return "anonymous";
        }
    }
    
    /**
     * Gets or creates a bucket for the given key
     */
    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::newBucket);
    }
    
    /**
     * Creates a new bucket based on the key type
     */
    private Bucket newBucket(String key) {
        if (key.startsWith("user:")) {
            // Authenticated user bucket
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(
                            authenticatedCapacity, 
                            Refill.intervally(authenticatedTokens, 
                                            Duration.ofSeconds(authenticatedPeriodSeconds))))
                    .build();
        } else {
            // Anonymous user bucket
            return Bucket.builder()
                    .addLimit(Bandwidth.classic(
                            anonymousCapacity, 
                            Refill.intervally(anonymousTokens, 
                                            Duration.ofSeconds(anonymousPeriodSeconds))))
                    .build();
        }
    }
    
    /**
     * Adds rate limit information headers to the response
     */
    private void addRateLimitHeaders(HttpServletResponse response, Bucket bucket) {
        long availableTokens = bucket.getAvailableTokens();
        response.addHeader("X-RateLimit-Limit", String.valueOf(getCapacity(bucket)));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
        response.addHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() / 1000 + getPeriodSeconds(bucket)));
    }
    
    /**
     * Handles rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(HttpServletResponse response, Bucket bucket) 
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.addHeader("X-RateLimit-Limit", String.valueOf(getCapacity(bucket)));
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() / 1000 + getPeriodSeconds(bucket)));
        response.addHeader("Retry-After", String.valueOf(getPeriodSeconds(bucket)));
        
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
        
        log.warn("Rate limit exceeded for request");
    }
    
    /**
     * Gets client IP address considering proxy headers
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Gets bucket capacity based on configuration
     */
    private long getCapacity(Bucket bucket) {
        // For simplicity, return configured values
        // In production, you might want to extract this from bucket configuration
        return bucket.getAvailableTokens() > anonymousCapacity ? 
               authenticatedCapacity : anonymousCapacity;
    }
    
    /**
     * Gets period in seconds based on configuration
     */
    private long getPeriodSeconds(Bucket bucket) {
        // For simplicity, return configured values
        return bucket.getAvailableTokens() > anonymousCapacity ? 
               authenticatedPeriodSeconds : anonymousPeriodSeconds;
    }
    
    /**
     * Exclude certain paths from rate limiting
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Don't rate limit health checks and static resources
        return path.startsWith("/actuator/") || 
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}