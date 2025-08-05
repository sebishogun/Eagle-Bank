package com.eaglebank.integration;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.pattern.strategy.TransactionStrategy;
import com.eaglebank.pattern.strategy.TransactionStrategyFactory;
import com.eaglebank.pattern.factory.AccountFactory;
import com.eaglebank.pattern.factory.AccountFactoryProvider;
import com.eaglebank.pattern.specification.Specification;
import com.eaglebank.pattern.specification.AccountSpecifications;
import com.eaglebank.pattern.specification.TransactionSpecifications;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.pattern.chain.TransactionValidationChain;
import com.eaglebank.pattern.decorator.MetricsTransactionDecorator;
import com.eaglebank.pattern.command.CreateAccountCommand;
import com.eaglebank.pattern.command.CommandInvoker;
import com.eaglebank.metrics.TransactionMetricsCollector;
import com.eaglebank.metrics.AccountMetricsCollector;
import com.eaglebank.cache.CacheStatisticsService;
import com.eaglebank.audit.AuditService;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.TransactionService;
import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import com.eaglebank.config.TestPatternConfig;
// removed Spring's Specification import - using our custom one
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestPatternConfig.class})
class PatternIntegrationTest {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionStrategyFactory strategyFactory;
    
    @Autowired
    private AccountFactoryProvider factoryProvider;
    
    @Autowired
    private TransactionValidationChain validationChain;
    
    @Autowired
    private CommandInvoker commandInvoker;
    
    @MockitoBean
    private EventPublisher eventPublisher;
    
    @MockitoBean
    private TransactionMetricsCollector transactionMetrics;
    
    @MockitoBean
    private AccountMetricsCollector accountMetrics;
    
    @MockitoBean
    private AuditService auditService;
    
    @MockitoBean
    private CacheStatisticsService cacheStatistics;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private User testUser;
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        // Create test user
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("integration@test.com")
                .password(passwordEncoder.encode("password123"))
                .firstName("Integration")
                .lastName("Test")
                .phoneNumber("+1234567890")
                .build();
        
        testUser = userRepository.save(testUser);
        userId = testUser.getId();
    }
    
    @Test
    @DisplayName("Should integrate Strategy and Factory patterns for account creation and transaction")
    void shouldIntegrateStrategyAndFactoryPatterns() {
        // Create account using Factory pattern
        CreateAccountRequest request = new CreateAccountRequest(
                Account.AccountType.SAVINGS,
                new BigDecimal("1000.00")
        );
        
        AccountResponse accountResponse = accountService.createAccount(userId, request);
        assertNotNull(accountResponse);
        assertEquals(Account.AccountType.SAVINGS, accountResponse.getAccountType());
        
        // Verify factory was used
        AccountFactory factory = factoryProvider.getFactory("SAVINGS");
        assertNotNull(factory);
        
        // Create transaction using Strategy pattern
        CreateTransactionRequest transactionRequest = new CreateTransactionRequest(
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("500.00"),
                "Test deposit"
        );
        
        TransactionResponse transactionResponse = transactionService.createTransaction(
                userId, accountResponse.getId(), transactionRequest);
        
        assertNotNull(transactionResponse);
        assertEquals(Transaction.TransactionType.DEPOSIT, transactionResponse.getTransactionType());
        assertEquals(new BigDecimal("500.00"), transactionResponse.getAmount());
        
        // Verify balance updated
        Account updatedAccount = accountRepository.findById(accountResponse.getId()).orElseThrow();
        assertEquals(new BigDecimal("1500.00"), updatedAccount.getBalance());
        
        // Verify metrics were collected
        verify(transactionMetrics).recordTransaction(any(), any(), anyLong());
        verify(accountMetrics).recordAccountCreated(any(), any());
    }
    
    @Test
    @DisplayName("Should integrate Specification pattern for complex queries")
    void shouldIntegrateSpecificationPattern() {
        // Create multiple accounts
        Account savings1 = createAccount("SAVINGS", new BigDecimal("5000.00"));
        Account savings2 = createAccount("SAVINGS", new BigDecimal("15000.00"));
        Account checking = createAccount("CHECKING", new BigDecimal("2000.00"));
        
        // Test account specifications
        Specification<Account> highValueSpec = AccountSpecifications.balanceGreaterThan(new BigDecimal("10000.00"));
        Specification<Account> savingsSpec = AccountSpecifications.hasAccountType("SAVINGS");
        Specification<Account> combinedSpec = highValueSpec.and(savingsSpec);
        
        List<Account> highValueSavings = accountRepository.findAll(combinedSpec);
        assertEquals(1, highValueSavings.size());
        assertEquals(savings2.getId(), highValueSavings.get(0).getId());
        
        // Create transactions
        createTransaction(savings1.getId(), Transaction.TransactionType.DEPOSIT, new BigDecimal("1000.00"));
        createTransaction(savings2.getId(), Transaction.TransactionType.WITHDRAWAL, new BigDecimal("5000.00"));
        createTransaction(checking.getId(), Transaction.TransactionType.DEPOSIT, new BigDecimal("500.00"));
        
        // Test transaction specifications
        Specification<Transaction> largeTransactionSpec = TransactionSpecifications.amountGreaterThan(new BigDecimal("1000.00"));
        List<Transaction> largeTransactions = transactionRepository.findAll(largeTransactionSpec);
        assertEquals(1, largeTransactions.size());
        assertEquals(new BigDecimal("5000.00"), largeTransactions.get(0).getAmount());
    }
    
    @Test
    @DisplayName("Should integrate Chain of Responsibility for validation")
    void shouldIntegrateChainOfResponsibility() {
        // Create account with initial balance
        Account account = createAccount("CHECKING", new BigDecimal("1000.00"));
        
        // Test valid withdrawal
        CreateTransactionRequest validRequest = new CreateTransactionRequest(
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("500.00"),
                "Valid withdrawal"
        );
        
        TransactionResponse response = transactionService.createTransaction(
                userId, account.getId(), validRequest);
        assertNotNull(response);
        
        // Test invalid withdrawal (insufficient funds)
        CreateTransactionRequest invalidRequest = new CreateTransactionRequest(
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("600.00"),
                "Should fail"
        );
        
        assertThrows(Exception.class, () -> 
            transactionService.createTransaction(userId, account.getId(), invalidRequest)
        );
    }
    
    @Test
    @DisplayName("Should integrate Command pattern with undo functionality")
    void shouldIntegrateCommandPattern() {
        // Create account using command
        CreateAccountRequest request = new CreateAccountRequest(Account.AccountType.SAVINGS, new BigDecimal("2000.00"));
        CreateAccountCommand command = new CreateAccountCommand(
                accountService, accountRepository, userId, request
        );
        
        AccountResponse response = commandInvoker.execute(command);
        assertNotNull(response);
        UUID accountId = response.getId();
        
        // Verify account exists
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertEquals(Account.AccountType.SAVINGS, account.getAccountType());
        assertEquals(new BigDecimal("2000.00"), account.getBalance());
        
        // Undo the command
        commandInvoker.undo();
        
        // Verify account is deleted
        assertFalse(accountRepository.findById(accountId).isPresent());
    }
    
    @Test
    @DisplayName("Should integrate Decorator pattern for enhanced transactions")
    void shouldIntegrateDecoratorPattern() {
        Account account = createAccount("CHECKING", new BigDecimal("1000.00"));
        
        // Create transaction that will be decorated with metrics
        CreateTransactionRequest request = new CreateTransactionRequest(
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("500.00"),
                "Decorated transaction"
        );
        
        TransactionResponse response = transactionService.createTransaction(
                userId, account.getId(), request);
        
        assertNotNull(response);
        
        // Verify metrics decorator was applied
        verify(transactionMetrics).recordTransaction(
                eq(Transaction.TransactionType.DEPOSIT),
                eq(new BigDecimal("500.00")),
                anyLong()
        );
    }
    
    @Test
    @DisplayName("Should integrate Observer pattern for event handling")
    void shouldIntegrateObserverPattern() {
        // Create account
        CreateAccountRequest request = new CreateAccountRequest(
                Account.AccountType.SAVINGS,
                new BigDecimal("5000.00")
        );
        
        AccountResponse accountResponse = accountService.createAccount(userId, request);
        
        // Verify event was published
        verify(eventPublisher).publishEvent(argThat(event ->
            event.getClass().getSimpleName().equals("AccountCreatedEvent")
        ));
        
        // Create transaction
        CreateTransactionRequest transactionRequest = new CreateTransactionRequest(
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("1000.00"),
                "Event test"
        );
        
        transactionService.createTransaction(userId, accountResponse.getId(), transactionRequest);
        
        // Verify transaction event was published
        verify(eventPublisher, times(2)).publishEvent(any());
    }
    
    @Test
    @DisplayName("Should integrate all patterns in complex workflow")
    void shouldIntegrateAllPatternsInComplexWorkflow() {
        // 1. Create account using Factory and Command patterns
        CreateAccountRequest request = new CreateAccountRequest(Account.AccountType.SAVINGS, new BigDecimal("10000.00"));
        CreateAccountCommand createCommand = new CreateAccountCommand(
                accountService, accountRepository, userId, request
        );
        AccountResponse accountResponse = commandInvoker.execute(createCommand);
        UUID accountId = accountResponse.getId();
        
        // 2. Verify events were published (Observer pattern)
        verify(eventPublisher).publishEvent(any());
        
        // 3. Create transactions using Strategy pattern
        for (int i = 0; i < 5; i++) {
            CreateTransactionRequest deposit = new CreateTransactionRequest(
                    Transaction.TransactionType.DEPOSIT,
                    new BigDecimal("1000.00"),
                    "Deposit " + i
            );
            transactionService.createTransaction(userId, accountId, deposit);
        }
        
        // 4. Query using Specification pattern
        com.eaglebank.pattern.specification.Specification<Transaction> spec = TransactionSpecifications
                .forAccount(accountId)
                .and(TransactionSpecifications.ofType(Transaction.TransactionType.DEPOSIT))
                .and(TransactionSpecifications.transactedAfter(LocalDateTime.now().minusDays(1)));
        
        List<Transaction> deposits = transactionRepository.findAll(spec);
        assertEquals(5, deposits.size());
        
        // 5. Verify metrics were collected (Decorator pattern)
        verify(transactionMetrics, times(5)).recordTransaction(any(), any(), anyLong());
        
        // 6. Test validation chain with large withdrawal
        CreateTransactionRequest withdrawal = new CreateTransactionRequest(
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("8000.00"),
                "Large withdrawal"
        );
        
        TransactionResponse withdrawalResponse = transactionService.createTransaction(
                userId, accountId, withdrawal);
        assertNotNull(withdrawalResponse);
        
        // 7. Verify final balance
        Account finalAccount = accountRepository.findById(accountId).orElseThrow();
        assertEquals(new BigDecimal("7000.00"), finalAccount.getBalance());
    }
    
    // Helper methods
    private Account createAccount(String type, BigDecimal initialBalance) {
        CreateAccountRequest request = new CreateAccountRequest(Account.AccountType.valueOf(type), initialBalance);
        AccountResponse response = accountService.createAccount(userId, request);
        return accountRepository.findById(response.getId()).orElseThrow();
    }
    
    private Transaction createTransaction(UUID accountId, Transaction.TransactionType type, BigDecimal amount) {
        CreateTransactionRequest request = new CreateTransactionRequest(type, amount, "Test");
        TransactionResponse response = transactionService.createTransaction(userId, accountId, request);
        return transactionRepository.findById(response.getId()).orElseThrow();
    }
}