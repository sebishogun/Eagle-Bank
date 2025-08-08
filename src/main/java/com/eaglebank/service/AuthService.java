package com.eaglebank.service;

import com.eaglebank.audit.Auditable;
import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.AuditService;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.entity.User;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.event.UserLoggedInEvent;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.metrics.AuthenticationMetricsCollector;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.security.JwtTokenProvider;
import com.eaglebank.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final AuthenticationMetricsCollector authMetricsCollector;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;
    
    public AuthResponse login(LoginRequest loginRequest) {
        log.debug("Attempting to authenticate user: {}", loginRequest.getEmail());
        
        // Check if account is locked
        if (loginAttemptService.isBlocked(loginRequest.getEmail())) {
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            
            // Audit blocked attempt
            auditService.auditFailedAccess(loginRequest.getEmail(), "Account locked", ipAddress, "/v1/auth/login");
            authMetricsCollector.recordFailedLogin(loginRequest.getEmail(), ipAddress);
            
            throw new UnauthorizedException("Account is locked due to too many failed login attempts. Please try again later.");
        }
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String jwt = tokenProvider.generateToken(userPrincipal.getId(), userPrincipal.getEmail(), userPrincipal.getSecurityVersion());
            
            Date expirationDate = tokenProvider.getExpirationDateFromToken(jwt);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                    expirationDate.toInstant(), 
                    ZoneId.systemDefault()
            );
            
            // Track the generated token for this user (for bulk revocation on password change)
            tokenBlacklistService.trackUserToken(userPrincipal.getId(), jwt, expirationDate);
            
            log.info("User {} authenticated successfully", loginRequest.getEmail());
            
            // Get request details
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";
            
            // Publish login event
            UserLoggedInEvent event = new UserLoggedInEvent(
                    userPrincipal.getId(),
                    userPrincipal.getEmail(),
                    ipAddress,
                    userAgent
            );
            eventPublisher.publishEvent(event);
            
            // Audit successful login
            auditService.auditLogin(userPrincipal.getId(), userPrincipal.getEmail(), ipAddress, userAgent);
            
            // Record metrics
            authMetricsCollector.recordSuccessfulLogin(userPrincipal.getEmail(), ipAddress);
            
            // Clear login attempts on successful login
            loginAttemptService.loginSucceeded(userPrincipal.getEmail());
            
            // Create refresh token
            String refreshToken = refreshTokenService.createRefreshToken(userPrincipal.getId());
            
            return AuthResponse.builder()
                    .token(jwt)
                    .type("Bearer")
                    .userId(userPrincipal.getId())
                    .email(userPrincipal.getEmail())
                    .expiresAt(expiresAt)
                    .refreshToken(refreshToken)
                    .build();
            
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            
            // Record failed login attempt
            loginAttemptService.loginFailed(loginRequest.getEmail());
            
            // Audit failed login
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            auditService.auditFailedAccess(loginRequest.getEmail(), "Invalid credentials", ipAddress, "/v1/auth/login");
            
            // Record metrics
            authMetricsCollector.recordFailedLogin(loginRequest.getEmail(), ipAddress);
            
            // Get remaining attempts
            int remainingAttempts = loginAttemptService.getRemainingAttempts(loginRequest.getEmail());
            String message = remainingAttempts > 0 
                ? String.format("Invalid email or password. %d attempts remaining.", remainingAttempts)
                : "Invalid email or password. Account is now locked.";
            
            throw new UnauthorizedException(message);
        } catch (AuthenticationException e) {
            log.error("Authentication error for user: {}", loginRequest.getEmail(), e);
            throw new UnauthorizedException("Authentication failed", e);
        }
    }
    
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) return "Unknown";
        
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Refreshes access token using refresh token
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Attempting to refresh access token");
        
        try {
            // Validate refresh token
            RefreshTokenService.RefreshTokenData tokenData = refreshTokenService.validateRefreshToken(refreshToken);
            
            // Get current user to get latest security version
            User user = userRepository.findById(tokenData.getUserId())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            
            // Generate new access token with current security version
            String jwt = tokenProvider.generateToken(tokenData.getUserId(), tokenData.getEmail(), user.getSecurityVersion());
            
            Date expirationDate = tokenProvider.getExpirationDateFromToken(jwt);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                    expirationDate.toInstant(), 
                    ZoneId.systemDefault()
            );
            
            // Rotate refresh token for better security
            String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);
            
            log.info("Token refreshed successfully for user: {}", tokenData.getEmail());
            
            // Audit token refresh
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            auditService.auditTokenRefresh(tokenData.getUserId(), tokenData.getEmail(), ipAddress);
            
            return AuthResponse.builder()
                    .token(jwt)
                    .type("Bearer")
                    .userId(tokenData.getUserId())
                    .email(tokenData.getEmail())
                    .expiresAt(expiresAt)
                    .refreshToken(newRefreshToken)
                    .build();
                    
        } catch (UnauthorizedException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            
            // Audit failed refresh
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            auditService.auditFailedAccess("Unknown", "Invalid refresh token", ipAddress, "/v1/auth/refresh");
            
            throw e;
        }
    }
}