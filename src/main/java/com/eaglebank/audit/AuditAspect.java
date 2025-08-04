package com.eaglebank.audit;

import com.eaglebank.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    
    private final AuditService auditService;
    
    @Pointcut("@annotation(auditable)")
    public void auditableMethod(Auditable auditable) {}
    
    @AfterReturning(pointcut = "auditableMethod(auditable)", returning = "result", argNames = "joinPoint,auditable,result")
    public void auditSuccessfulOperation(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            AuditEntry entry = createAuditEntry(joinPoint, auditable);
            entry.setStatusCode(200);
            
            // Add method arguments as details
            Map<String, String> details = new HashMap<>();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    details.put("arg" + i, args[i].toString());
                }
            }
            entry.setDetails(details);
            
            auditService.audit(entry);
        } catch (Exception e) {
            log.error("Failed to audit successful operation: {}", e.getMessage());
        }
    }
    
    @AfterThrowing(pointcut = "auditableMethod(auditable)", throwing = "exception", argNames = "joinPoint,auditable,exception")
    public void auditFailedOperation(JoinPoint joinPoint, Auditable auditable, Exception exception) {
        try {
            AuditEntry entry = createAuditEntry(joinPoint, auditable);
            entry.setStatusCode(500);
            entry.setErrorMessage(exception.getMessage());
            
            auditService.audit(entry);
        } catch (Exception e) {
            log.error("Failed to audit failed operation: {}", e.getMessage());
        }
    }
    
    private AuditEntry createAuditEntry(JoinPoint joinPoint, Auditable auditable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpServletRequest request = getRequest();
        
        AuditEntry.AuditEntryBuilder builder = AuditEntry.builder()
                .action(auditable.action())
                .entityType(auditable.entityType());
        
        // Set user information
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            builder.userId(principal.getId())
                   .username(principal.getUsername());
        }
        
        // Set request information
        if (request != null) {
            builder.ipAddress(getClientIpAddress(request))
                   .userAgent(request.getHeader("User-Agent"))
                   .requestMethod(request.getMethod())
                   .requestPath(request.getRequestURI());
        }
        
        // Set entity ID if available
        if (!auditable.entityIdParam().isEmpty()) {
            Object[] args = joinPoint.getArgs();
            String[] paramNames = auditable.entityIdParam().split("\\.");
            int paramIndex = Integer.parseInt(paramNames[0]);
            
            if (paramIndex < args.length && args[paramIndex] != null) {
                builder.entityId(args[paramIndex].toString());
            }
        }
        
        return builder.build();
    }
    
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
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