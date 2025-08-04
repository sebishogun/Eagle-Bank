package com.eaglebank.service;

import com.eaglebank.audit.Auditable;
import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.AuditService;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.event.UserLoggedInEvent;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.metrics.AuthenticationMetricsCollector;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final AuthenticationMetricsCollector authMetricsCollector;
    
    public AuthResponse login(LoginRequest loginRequest) {
        log.debug("Attempting to authenticate user: {}", loginRequest.getEmail());
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String jwt = tokenProvider.generateToken(userPrincipal.getId(), userPrincipal.getEmail());
            
            Date expirationDate = tokenProvider.getExpirationDateFromToken(jwt);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                    expirationDate.toInstant(), 
                    ZoneId.systemDefault()
            );
            
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
            
            return AuthResponse.builder()
                    .token(jwt)
                    .type("Bearer")
                    .userId(userPrincipal.getId())
                    .email(userPrincipal.getEmail())
                    .expiresAt(expiresAt)
                    .build();
            
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            
            // Audit failed login
            HttpServletRequest request = getRequest();
            String ipAddress = getClientIpAddress(request);
            auditService.auditFailedAccess(loginRequest.getEmail(), "Invalid credentials", ipAddress, "/v1/auth/login");
            
            // Record metrics
            authMetricsCollector.recordFailedLogin(loginRequest.getEmail(), ipAddress);
            
            throw new UnauthorizedException("Invalid email or password");
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
}