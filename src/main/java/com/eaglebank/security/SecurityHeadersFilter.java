package com.eaglebank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that adds important security headers to all HTTP responses.
 * This filter runs early in the filter chain to ensure headers are set for all responses.
 */
@Component
@Order(1)
@Slf4j
public class SecurityHeadersFilter extends OncePerRequestFilter {
    
    // Security header constants
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String PERMISSIONS_POLICY = "Permissions-Policy";
    private static final String X_PERMITTED_CROSS_DOMAIN_POLICIES = "X-Permitted-Cross-Domain-Policies";
    
    // Header values
    private static final String HSTS_VALUE = "max-age=31536000; includeSubDomains; preload";
    private static final String FRAME_OPTIONS_VALUE = "DENY";
    private static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    private static final String XSS_PROTECTION_VALUE = "1; mode=block";
    private static final String CSP_VALUE = "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " + // For Swagger UI
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " + // For Swagger UI
            "img-src 'self' data: https:; " +
            "font-src 'self' https://cdn.jsdelivr.net; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "form-action 'self'; " +
            "base-uri 'self'; " +
            "object-src 'none'";
    private static final String REFERRER_POLICY_VALUE = "strict-origin-when-cross-origin";
    private static final String PERMISSIONS_POLICY_VALUE = "geolocation=(), microphone=(), camera=(), " +
            "usb=(), payment=(), magnetometer=(), accelerometer=(), gyroscope=()";
    private static final String CROSS_DOMAIN_VALUE = "none";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Add security headers to response
        addSecurityHeaders(response);
        
        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }
    
    /**
     * Adds all security headers to the HTTP response
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // HSTS - Enforces HTTPS connections
        response.setHeader(STRICT_TRANSPORT_SECURITY, HSTS_VALUE);
        
        // Prevents clickjacking attacks
        response.setHeader(X_FRAME_OPTIONS, FRAME_OPTIONS_VALUE);
        
        // Prevents MIME type sniffing
        response.setHeader(X_CONTENT_TYPE_OPTIONS, CONTENT_TYPE_OPTIONS_VALUE);
        
        // Enables XSS protection in older browsers
        response.setHeader(X_XSS_PROTECTION, XSS_PROTECTION_VALUE);
        
        // Content Security Policy - Prevents various injection attacks
        response.setHeader(CONTENT_SECURITY_POLICY, CSP_VALUE);
        
        // Controls referrer information
        response.setHeader(REFERRER_POLICY, REFERRER_POLICY_VALUE);
        
        // Permissions Policy (formerly Feature Policy)
        response.setHeader(PERMISSIONS_POLICY, PERMISSIONS_POLICY_VALUE);
        
        // Adobe Flash/PDF cross-domain policy
        response.setHeader(X_PERMITTED_CROSS_DOMAIN_POLICIES, CROSS_DOMAIN_VALUE);
        
        log.debug("Security headers added to response");
    }
    
    /**
     * Exclude certain paths from security headers if needed
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Allow health check endpoints without strict headers if needed
        return path.equals("/actuator/health") || 
               path.equals("/actuator/info");
    }
}