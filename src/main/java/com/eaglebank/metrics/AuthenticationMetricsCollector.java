package com.eaglebank.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AuthenticationMetricsCollector implements MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter successfulLogins;
    private final Counter failedLogins;
    private final AtomicLong activeSessionsGauge = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicInteger> failedLoginsByUser = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<LoginAttempt> recentLoginAttempts = new ConcurrentLinkedQueue<>();
    private final Map<MetricWindow, WindowedAuthMetrics> windowMetrics = new ConcurrentHashMap<>();
    private LocalDateTime lastResetTime = LocalDateTime.now();
    
    public AuthenticationMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.successfulLogins = Counter.builder("authentication.logins.successful")
                .description("Number of successful login attempts")
                .register(meterRegistry);
        
        this.failedLogins = Counter.builder("authentication.logins.failed")
                .description("Number of failed login attempts")
                .register(meterRegistry);
        
        // Active sessions gauge
        Gauge.builder("authentication.sessions.active", activeSessionsGauge, AtomicLong::get)
                .description("Number of active sessions")
                .register(meterRegistry);
        
        // Success rate gauge
        Gauge.builder("authentication.logins.success.rate", this, AuthenticationMetricsCollector::calculateSuccessRate)
                .description("Login success rate percentage")
                .baseUnit("percent")
                .register(meterRegistry);
        
        // Initialize time windows
        for (MetricWindow window : MetricWindow.values()) {
            windowMetrics.put(window, new WindowedAuthMetrics(window));
            
            // Create gauges for window metrics
            String windowName = window.name().toLowerCase();
            WindowedAuthMetrics windowMetric = windowMetrics.get(window);
            
            Gauge.builder("authentication.window.successful", windowMetric, wm -> wm.getSuccessfulCount())
                    .tag("window", windowName)
                    .description("Successful logins in time window")
                    .register(meterRegistry);
            
            Gauge.builder("authentication.window.failed", windowMetric, wm -> wm.getFailedCount())
                    .tag("window", windowName)
                    .description("Failed logins in time window")
                    .register(meterRegistry);
            
            Gauge.builder("authentication.window.rate", windowMetric, wm -> wm.getRate())
                    .tag("window", windowName)
                    .description("Login attempts per minute in time window")
                    .register(meterRegistry);
        }
        
        // Unique failed users gauge
        Gauge.builder("authentication.failed.unique.users", failedLoginsByUser, Map::size)
                .description("Number of unique users with failed login attempts")
                .register(meterRegistry);
    }
    
    @Override
    public String getMetricName() {
        return "authentication_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Basic counts
        double successful = successfulLogins.count();
        double failed = failedLogins.count();
        long active = activeSessionsGauge.get();
        
        metrics.put("successful_logins", (long) successful);
        metrics.put("failed_logins", (long) failed);
        metrics.put("active_sessions", active);
        metrics.put("total_attempts", (long) (successful + failed));
        
        // Success rate
        metrics.put("success_rate_percent", String.format("%.2f", calculateSuccessRate()));
        
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
        
        // Time info
        Map<String, Object> timeInfo = new HashMap<>();
        timeInfo.put("last_reset", lastResetTime.toString());
        timeInfo.put("collection_period_hours", 
            java.time.Duration.between(lastResetTime, LocalDateTime.now()).toHours());
        metrics.put("time_info", timeInfo);
        
        return metrics;
    }
    
    @Override
    public void reset() {
        // Note: Prometheus counters are cumulative and cannot be reset
        failedLoginsByUser.clear();
        recentLoginAttempts.clear();
        windowMetrics.values().forEach(WindowedAuthMetrics::reset);
        lastResetTime = LocalDateTime.now();
        log.info("Authentication metrics reset timestamp updated - Note: Prometheus counters are cumulative");
    }
    
    public void recordSuccessfulLogin(String username, String ipAddress) {
        successfulLogins.increment();
        activeSessionsGauge.incrementAndGet();
        
        // Record by IP counter
        meterRegistry.counter("authentication.logins.by.ip", "ip", ipAddress, "result", "success").increment();
        
        LoginAttempt attempt = new LoginAttempt(username, ipAddress, true, LocalDateTime.now());
        recordLoginAttempt(attempt);
        
        log.debug("Recorded successful login for user: {} from IP: {}", username, ipAddress);
    }
    
    public void recordFailedLogin(String username, String ipAddress) {
        failedLogins.increment();
        failedLoginsByUser.computeIfAbsent(username, k -> new AtomicInteger(0)).incrementAndGet();
        
        // Record by IP counter
        meterRegistry.counter("authentication.logins.by.ip", "ip", ipAddress, "result", "failed").increment();
        
        LoginAttempt attempt = new LoginAttempt(username, ipAddress, false, LocalDateTime.now());
        recordLoginAttempt(attempt);
        
        log.debug("Recorded failed login for user: {} from IP: {}", username, ipAddress);
    }
    
    public void recordLogout() {
        activeSessionsGauge.decrementAndGet();
        meterRegistry.counter("authentication.logouts").increment();
    }
    
    public void recordSessionExpired() {
        activeSessionsGauge.decrementAndGet();
        meterRegistry.counter("authentication.sessions.expired").increment();
    }
    
    private void recordLoginAttempt(LoginAttempt attempt) {
        recentLoginAttempts.offer(attempt);
        if (recentLoginAttempts.size() > 10000) {
            recentLoginAttempts.poll(); // Keep only recent 10k attempts
        }
        
        // Update time windows
        windowMetrics.values().forEach(window -> window.record(attempt));
    }
    
    private double calculateSuccessRate() {
        double successful = successfulLogins.count();
        double failed = failedLogins.count();
        double total = successful + failed;
        return total > 0 ? (successful / total * 100) : 0;
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
        
        long getSuccessfulCount() {
            return attempts.stream().filter(a -> a.successful).count();
        }
        
        long getFailedCount() {
            return attempts.stream().filter(a -> !a.successful).count();
        }
        
        double getRate() {
            return (double) attempts.size() / (window.getSeconds() / 60.0);
        }
        
        Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            long successful = getSuccessfulCount();
            long failed = getFailedCount();
            long total = attempts.size();
            
            metrics.put("successful", successful);
            metrics.put("failed", failed);
            metrics.put("total", total);
            
            if (total > 0) {
                metrics.put("success_rate", String.format("%.2f", (double) successful / total * 100));
                metrics.put("rate_per_minute", getRate());
                
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