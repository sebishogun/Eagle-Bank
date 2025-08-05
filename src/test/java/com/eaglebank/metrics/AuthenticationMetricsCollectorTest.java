package com.eaglebank.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationMetricsCollectorTest {
    
    private AuthenticationMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        collector = new AuthenticationMetricsCollector();
    }
    
    @Test
    @DisplayName("Should return correct metric name")
    void shouldReturnCorrectMetricName() {
        assertEquals("authentication_metrics", collector.getMetricName());
    }
    
    @Test
    @DisplayName("Should record successful login")
    void shouldRecordSuccessfulLogin() {
        collector.recordSuccessfulLogin("user@example.com", "192.168.1.100");
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(1, metrics.get("successful_logins"));
        assertEquals(0, metrics.get("failed_logins"));
        assertEquals(1, metrics.get("active_sessions"));
        assertEquals(1, metrics.get("total_attempts"));
        assertEquals("100.00", metrics.get("success_rate_percent"));
    }
    
    @Test
    @DisplayName("Should record failed login")
    void shouldRecordFailedLogin() {
        collector.recordFailedLogin("user@example.com", "192.168.1.100");
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(0, metrics.get("successful_logins"));
        assertEquals(1, metrics.get("failed_logins"));
        assertEquals(0, metrics.get("active_sessions"));
        assertEquals(1, metrics.get("total_attempts"));
        assertEquals("0.00", metrics.get("success_rate_percent"));
        
        Map<String, Object> failedAnalysis = (Map<String, Object>) metrics.get("failed_login_analysis");
        assertEquals(1, failedAnalysis.get("unique_users_with_failures"));
    }
    
    @Test
    @DisplayName("Should track multiple failed logins by user")
    void shouldTrackMultipleFailedLoginsByUser() {
        collector.recordFailedLogin("user1@example.com", "192.168.1.100");
        collector.recordFailedLogin("user1@example.com", "192.168.1.101");
        collector.recordFailedLogin("user2@example.com", "192.168.1.102");
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(3, metrics.get("failed_logins"));
        
        Map<String, Object> failedAnalysis = (Map<String, Object>) metrics.get("failed_login_analysis");
        assertEquals(2, failedAnalysis.get("unique_users_with_failures"));
        
        Map<String, Integer> topFailures = (Map<String, Integer>) failedAnalysis.get("top_failed_users");
        assertTrue(topFailures.containsKey("user1@example.com"));
        assertEquals(2, topFailures.get("user1@example.com"));
    }
    
    @Test
    @DisplayName("Should calculate success rate correctly")
    void shouldCalculateSuccessRate() {
        // Record mixed attempts
        collector.recordSuccessfulLogin("user1@example.com", "192.168.1.100");
        collector.recordSuccessfulLogin("user2@example.com", "192.168.1.101");
        collector.recordFailedLogin("user3@example.com", "192.168.1.102");
        collector.recordSuccessfulLogin("user4@example.com", "192.168.1.103");
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(3, metrics.get("successful_logins"));
        assertEquals(1, metrics.get("failed_logins"));
        assertEquals(4, metrics.get("total_attempts"));
        assertEquals("75.00", metrics.get("success_rate_percent"));
    }
    
    @Test
    @DisplayName("Should track logout and session expiry")
    void shouldTrackLogoutAndSessionExpiry() {
        // Login users
        collector.recordSuccessfulLogin("user1@example.com", "192.168.1.100");
        collector.recordSuccessfulLogin("user2@example.com", "192.168.1.101");
        
        Map<String, Object> beforeLogout = collector.collect();
        assertEquals(2, beforeLogout.get("active_sessions"));
        
        // Logout one user
        collector.recordLogout();
        
        Map<String, Object> afterLogout = collector.collect();
        assertEquals(1, afterLogout.get("active_sessions"));
        
        // Session expires
        collector.recordSessionExpired();
        
        Map<String, Object> afterExpiry = collector.collect();
        assertEquals(0, afterExpiry.get("active_sessions"));
    }
    
    @Test
    @DisplayName("Should track time window metrics")
    void shouldTrackTimeWindowMetrics() {
        collector.recordSuccessfulLogin("user@example.com", "192.168.1.100");
        collector.recordFailedLogin("hacker@example.com", "192.168.1.200");
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> timeWindows = (Map<String, Object>) metrics.get("time_windows");
        
        assertNotNull(timeWindows);
        
        Map<String, Object> oneMinute = (Map<String, Object>) timeWindows.get("one_minute");
        assertEquals(1L, oneMinute.get("successful"));
        assertEquals(1L, oneMinute.get("failed"));
        assertEquals(2L, oneMinute.get("total"));
        assertEquals("50.00", oneMinute.get("success_rate"));
        assertTrue(oneMinute.containsKey("rate_per_minute"));
        assertEquals(2L, oneMinute.get("unique_ips"));
    }
    
    @Test
    @DisplayName("Should reset metrics correctly")
    void shouldResetMetrics() {
        // Record some activity
        collector.recordSuccessfulLogin("user@example.com", "192.168.1.100");
        collector.recordFailedLogin("hacker@example.com", "192.168.1.200");
        
        // Verify metrics exist
        Map<String, Object> beforeReset = collector.collect();
        assertEquals(1, beforeReset.get("successful_logins"));
        assertEquals(1, beforeReset.get("failed_logins"));
        
        // Reset
        collector.reset();
        
        // Verify metrics are cleared
        Map<String, Object> afterReset = collector.collect();
        assertEquals(0, afterReset.get("successful_logins"));
        assertEquals(0, afterReset.get("failed_logins"));
        assertEquals(0, afterReset.get("total_attempts"));
        
        Map<String, Object> failedAnalysis = (Map<String, Object>) afterReset.get("failed_login_analysis");
        assertEquals(0, failedAnalysis.get("unique_users_with_failures"));
    }
}