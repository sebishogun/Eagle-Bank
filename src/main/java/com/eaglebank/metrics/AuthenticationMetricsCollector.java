package com.eaglebank.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AuthenticationMetricsCollector implements MetricsCollector {
    
    private final AtomicInteger successfulLogins = new AtomicInteger(0);
    private final AtomicInteger failedLogins = new AtomicInteger(0);
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> failedLoginsByUser = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<LoginAttempt> recentLoginAttempts = new ConcurrentLinkedQueue<>();
    private final Map<MetricWindow, WindowedAuthMetrics> windowMetrics = new ConcurrentHashMap<>();
    
    public AuthenticationMetricsCollector() {
        // Initialize time windows
        for (MetricWindow window : MetricWindow.values()) {
            windowMetrics.put(window, new WindowedAuthMetrics(window));
        }
    }
    
    @Override
    public String getMetricName() {
        return "authentication_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Basic counts
        metrics.put("successful_logins", successfulLogins.get());
        metrics.put("failed_logins", failedLogins.get());
        metrics.put("active_sessions", activeSessions.get());
        metrics.put("total_attempts", successfulLogins.get() + failedLogins.get());
        
        // Success rate
        int total = successfulLogins.get() + failedLogins.get();
        double successRate = total > 0 ? (double) successfulLogins.get() / total * 100 : 0;
        metrics.put("success_rate_percent", String.format("%.2f", successRate));
        
        // Failed login analysis
        Map<String, Object> failedAnalysis = new HashMap<>();
        failedAnalysis.put("unique_users_with_failures", failedLoginsByUser.size());
        
        // Find users with most failures
        Map<String, Integer> topFailures = new HashMap<>();
        failedLoginsByUser.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().get() - e1.getValue().get())
            .limit(5)
            .forEach(e -> topFailures.put(e.getKey(), e.getValue().get()));
        failedAnalysis.put("top_failed_users", topFailures);
        
        metrics.put("failed_login_analysis", failedAnalysis);
        
        // Time window metrics
        Map<String, Object> windows = new HashMap<>();
        for (Map.Entry<MetricWindow, WindowedAuthMetrics> entry : windowMetrics.entrySet()) {
            windows.put(entry.getKey().name().toLowerCase(), entry.getValue().getMetrics());
        }
        metrics.put("time_windows", windows);
        
        return metrics;
    }
    
    @Override
    public void reset() {
        successfulLogins.set(0);
        failedLogins.set(0);
        failedLoginsByUser.clear();
        recentLoginAttempts.clear();
        windowMetrics.values().forEach(WindowedAuthMetrics::reset);
        log.info("Authentication metrics reset");
    }
    
    public void recordSuccessfulLogin(String username, String ipAddress) {
        successfulLogins.incrementAndGet();
        activeSessions.incrementAndGet();
        
        LoginAttempt attempt = new LoginAttempt(username, ipAddress, true, LocalDateTime.now());
        recordLoginAttempt(attempt);
        
        log.debug("Recorded successful login for user: {} from IP: {}", username, ipAddress);
    }
    
    public void recordFailedLogin(String username, String ipAddress) {
        failedLogins.incrementAndGet();
        failedLoginsByUser.computeIfAbsent(username, k -> new AtomicInteger(0)).incrementAndGet();
        
        LoginAttempt attempt = new LoginAttempt(username, ipAddress, false, LocalDateTime.now());
        recordLoginAttempt(attempt);
        
        log.debug("Recorded failed login for user: {} from IP: {}", username, ipAddress);
    }
    
    public void recordLogout() {
        activeSessions.decrementAndGet();
    }
    
    public void recordSessionExpired() {
        activeSessions.decrementAndGet();
    }
    
    private void recordLoginAttempt(LoginAttempt attempt) {
        recentLoginAttempts.offer(attempt);
        if (recentLoginAttempts.size() > 10000) {
            recentLoginAttempts.poll(); // Keep only recent 10k attempts
        }
        
        // Update time windows
        windowMetrics.values().forEach(window -> window.record(attempt));
    }
    
    private static class LoginAttempt {
        final String username;
        final String ipAddress;
        final boolean successful;
        final LocalDateTime timestamp;
        
        LoginAttempt(String username, String ipAddress, boolean successful, LocalDateTime timestamp) {
            this.username = username;
            this.ipAddress = ipAddress;
            this.successful = successful;
            this.timestamp = timestamp;
        }
    }
    
    private static class WindowedAuthMetrics {
        private final MetricWindow window;
        private final ConcurrentLinkedQueue<LoginAttempt> attempts = new ConcurrentLinkedQueue<>();
        
        WindowedAuthMetrics(MetricWindow window) {
            this.window = window;
        }
        
        void record(LoginAttempt attempt) {
            attempts.offer(attempt);
            
            // Remove old attempts outside window
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(window.getSeconds());
            attempts.removeIf(a -> a.timestamp.isBefore(cutoff));
        }
        
        Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            long successful = attempts.stream().filter(a -> a.successful).count();
            long failed = attempts.stream().filter(a -> !a.successful).count();
            long total = attempts.size();
            
            metrics.put("successful", successful);
            metrics.put("failed", failed);
            metrics.put("total", total);
            
            if (total > 0) {
                metrics.put("success_rate", String.format("%.2f", (double) successful / total * 100));
                metrics.put("rate_per_minute", (double) total / (window.getSeconds() / 60.0));
                
                // Unique IPs
                long uniqueIps = attempts.stream()
                    .map(a -> a.ipAddress)
                    .distinct()
                    .count();
                metrics.put("unique_ips", uniqueIps);
            }
            
            return metrics;
        }
        
        void reset() {
            attempts.clear();
        }
    }
}