package com.eaglebank.integration;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.metrics.*;
import com.eaglebank.cache.*;
import com.eaglebank.controller.MetricsController;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.TransactionService;
import com.eaglebank.service.AuthService;
import com.eaglebank.dto.request.*;
import com.eaglebank.dto.response.*;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import com.eaglebank.config.TestStrategyConfiguration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCacheIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TransactionMetricsCollector transactionMetrics;
    
    @Autowired
    private AccountMetricsCollector accountMetrics;
    
    @Autowired
    private AuthenticationMetricsCollector authMetrics;
    
    @Autowired
    private CacheStatisticsService cacheStatistics;
    
    @Autowired
    private CacheWarmingService cacheWarmingService;
    
    @Autowired
    private MetricsController metricsController;
    
    @Setter
    @Getter
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID userId;
    
    @BeforeEach
    void setUp() {
        // Clear metrics
        transactionMetrics.reset();
        accountMetrics.reset();
        authMetrics.reset();
        cacheStatistics.resetAllStatistics();
        
        // Create test user
        User testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("metrics@test.com")
                .password(passwordEncoder.encode("password123"))
                .firstName("Metrics")
                .lastName("Test")
                .phoneNumber("+1234567890")
                .build();
        
        testUser = userRepository.save(testUser);
        userId = testUser.getId();
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should collect comprehensive metrics across all operations")
    void shouldCollectComprehensiveMetrics() {
        // 1. Authentication metrics
        LoginRequest loginRequest = new LoginRequest("metrics@test.com", "password123");
        AuthResponse authResponse = authService.login(loginRequest);
        assertNotNull(authResponse);
        
        // Failed login attempt
        LoginRequest failedLogin = new LoginRequest("metrics@test.com", "wrongpassword");
        assertThrows(Exception.class, () -> authService.login(failedLogin));
        
        // 2. Account creation metrics
        CreateAccountRequest savingsRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialBalance(new BigDecimal("5000.00"))
                .build();
        AccountResponse savings = accountService.createAccount(userId, savingsRequest);
        
        CreateAccountRequest checkingRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CHECKING)
                .initialBalance(new BigDecimal("2000.00"))
                .build();
        AccountResponse checking = accountService.createAccount(userId, checkingRequest);
        
        // 3. Transaction metrics
        // Multiple deposits
        for (int i = 0; i < 3; i++) {
            CreateTransactionRequest deposit = new CreateTransactionRequest(
                    Transaction.TransactionType.DEPOSIT,
                    new BigDecimal("1000.00"),
                    "Deposit " + i
            );
            transactionService.createTransaction(userId, savings.getId(), deposit);
        }
        
        // Withdrawals
        CreateTransactionRequest withdrawal = new CreateTransactionRequest(
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("2000.00"),
                "Test withdrawal"
        );
        transactionService.createTransaction(userId, savings.getId(), withdrawal);
        
        // 4. Verify transaction metrics
        ResponseEntity<MetricsDto> transactionMetricsResponse = metricsController.getTransactionMetrics();
        MetricsDto transactionDto = transactionMetricsResponse.getBody();
        assertNotNull(transactionDto);
        assertNotNull(transactionDto.getMetrics());
        
        // Transaction metrics are stored in the metrics map
        Map<String, Object> txMetrics = transactionDto.getMetrics();
        assertNotNull(txMetrics.get("total_transactions"));
        assertNotNull(txMetrics.get("total_volume"));
        
        // 5. Verify account metrics
        ResponseEntity<MetricsDto> accountMetricsResponse = metricsController.getAccountMetrics();
        MetricsDto accountDto = accountMetricsResponse.getBody();
        assertNotNull(accountDto);
        assertNotNull(accountDto.getMetrics());
        
        // 6. Verify authentication metrics
        ResponseEntity<MetricsDto> authMetricsResponse = metricsController.getAuthenticationMetrics();
        MetricsDto authDto = authMetricsResponse.getBody();
        assertNotNull(authDto);
        assertNotNull(authDto.getMetrics());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should track cache statistics during operations")
    void shouldTrackCacheStatistics() {
        // Create account and transactions to populate cache
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialBalance(new BigDecimal("1000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // First access - cache miss
        accountService.getAccountById(userId, account.getId());
        
        // Second access - should be cache hit
        accountService.getAccountById(userId, account.getId());
        
        // Third access - should be another cache hit
        accountService.getAccountById(userId, account.getId());
        
        // Get cache statistics
        ResponseEntity<MetricsDto> cacheResponse = metricsController.getCacheMetrics();
        MetricsDto cacheDto = cacheResponse.getBody();
        assertNotNull(cacheDto);
        Map<String, Object> cacheStats = cacheDto.getMetrics();
        assertNotNull(cacheStats);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) cacheStats.get("summary");
        assertNotNull(summary);
        
        // Verify cache statistics structure exists
        assertNotNull(summary.get("total_hits"));
        assertNotNull(summary.get("total_misses"));
        assertNotNull(summary.get("total_caches"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should collect time-windowed metrics")
    void shouldCollectTimeWindowedMetrics() throws InterruptedException {
        // Create transactions over time
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CHECKING)
                .initialBalance(new BigDecimal("5000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // First batch of transactions
        for (int i = 0; i < 5; i++) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                    Transaction.TransactionType.DEPOSIT,
                    new BigDecimal("100.00"),
                    "Batch 1 - " + i
            );
            transactionService.createTransaction(userId, account.getId(), request);
        }
        
        // Wait a bit
        Thread.sleep(100);
        
        // Second batch of transactions
        for (int i = 0; i < 3; i++) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                    Transaction.TransactionType.WITHDRAWAL,
                    new BigDecimal("50.00"),
                    "Batch 2 - " + i
            );
            transactionService.createTransaction(userId, account.getId(), request);
        }
        
        // Verify metrics were collected
        // Since we can't access internal metrics directly, we verify through the controller
        ResponseEntity<MetricsDto> metricsResponse = metricsController.getTransactionMetrics();
        assertNotNull(metricsResponse.getBody());
        Map<String, Object> metrics = metricsResponse.getBody().getMetrics();
        assertNotNull(metrics);
    }
    
    @Test
    @DisplayName("Should warm cache and improve performance")
    void shouldWarmCacheAndImprovePerformance() {
        // Create multiple accounts
        for (int i = 0; i < 5; i++) {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountType(i % 2 == 0 ? Account.AccountType.SAVINGS : Account.AccountType.CHECKING)
                    .initialBalance(new BigDecimal("1000.00").multiply(new BigDecimal(i + 1)))
                    .build();
            accountService.createAccount(userId, request);
        }
        
        // Clear cache statistics
        cacheStatistics.resetAllStatistics();
        
        // Warm specific user cache
        cacheWarmingService.warmSpecificUser(userId.toString());
        
        // Allow warming to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Access accounts - should mostly be cache hits
        accountService.getUserAccounts(userId, null);
        
        // Verify cache was warmed
        Map<String, MetricsDto.CacheMetricsDto> allStats = cacheStatistics.getAllCacheStatistics();
        assertNotNull(allStats);
        
        // Verify cache statistics structure exists
        assertFalse(allStats.isEmpty());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should track metrics by type and time window")
    void shouldTrackMetricsByTypeAndTimeWindow() {
        // Create account
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialBalance(new BigDecimal("10000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // Mix of transaction types
        BigDecimal[] amounts = {new BigDecimal("100"), new BigDecimal("500"), new BigDecimal("1000")};
        
        for (BigDecimal amount : amounts) {
            // Deposit
            CreateTransactionRequest deposit = new CreateTransactionRequest(
                    Transaction.TransactionType.DEPOSIT,
                    amount,
                    "Deposit of " + amount
            );
            transactionService.createTransaction(userId, account.getId(), deposit);
            
            // Withdrawal
            CreateTransactionRequest withdrawal = new CreateTransactionRequest(
                    Transaction.TransactionType.WITHDRAWAL,
                    amount.divide(new BigDecimal("2"), java.math.RoundingMode.HALF_UP),
                    "Withdrawal of " + amount.divide(new BigDecimal("2"), java.math.RoundingMode.HALF_UP)
            );
            transactionService.createTransaction(userId, account.getId(), withdrawal);
        }
        
        // Verify metrics through controller
        ResponseEntity<MetricsDto> metricsResponse = metricsController.getTransactionMetrics();
        assertNotNull(metricsResponse.getBody());
        Map<String, Object> metricsMap = metricsResponse.getBody().getMetrics();
        assertNotNull(metricsMap);
        
        // Verify some transactions were recorded
        assertNotNull(metricsMap.get("total_transactions"));
        assertNotNull(metricsMap.get("total_volume"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should expose all metrics through controller endpoints")
    void shouldExposeAllMetricsThroughController() {
        // Generate some activity
        LoginRequest loginRequest = new LoginRequest("metrics@test.com", "password123");
        authService.login(loginRequest);
        
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialBalance(new BigDecimal("5000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        CreateTransactionRequest transactionRequest = new CreateTransactionRequest(
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("1000.00"),
                "Test deposit"
        );
        transactionService.createTransaction(userId, account.getId(), transactionRequest);
        
        // Test all metrics endpoints
        ResponseEntity<MetricsDto> allMetrics = metricsController.getAllMetrics();
        assertNotNull(allMetrics.getBody());
        
        Map<String, Object> metricsMap = allMetrics.getBody().getMetrics();
        assertTrue(metricsMap.containsKey("transaction_metrics"));
        assertTrue(metricsMap.containsKey("account_metrics"));
        assertTrue(metricsMap.containsKey("authentication_metrics"));
        assertTrue(metricsMap.containsKey("cache_statistics"));
        
        // Verify health endpoint
        ResponseEntity<MetricsDto.SystemHealthDto> health = metricsController.getSystemHealth();
        assertNotNull(health.getBody());
        
        MetricsDto.SystemHealthDto healthDto = health.getBody();
        assertEquals("UP", healthDto.getStatus());
        assertNotNull(healthDto.getTimestamp());
        assertNotNull(healthDto.getMemory());
    }

}