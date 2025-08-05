package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.TransactionService;
import com.eaglebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import com.eaglebank.config.TestStrategyConfiguration;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class CreditAccountIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .email("credit.test@example.com")
                .password("SecurePass123!")
                .firstName("Credit")
                .lastName("Test")
                .build();
        UserResponse user = userService.createUser(userRequest);
        userId = user.getId();
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should create credit account with default credit limit")
    void shouldCreateCreditAccountWithDefaultLimit() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .accountName("My Credit Card")
                .initialBalance(BigDecimal.ZERO)
                .build();
        
        AccountResponse account = accountService.createAccount(userId, request);
        
        assertNotNull(account);
        assertEquals(Account.AccountType.CREDIT, account.getAccountType());
        assertEquals("My Credit Card", account.getAccountName());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(new BigDecimal("5000.00"), account.getCreditLimit());
        assertEquals(new BigDecimal("5000.00"), account.getAvailableCredit());
        assertTrue(account.getAccountNumber().startsWith("4"));
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should create credit account with custom credit limit")
    void shouldCreateCreditAccountWithCustomLimit() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .accountName("Premium Credit Card")
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("10000.00"))
                .build();
        
        AccountResponse account = accountService.createAccount(userId, request);
        
        assertNotNull(account);
        assertEquals(new BigDecimal("10000.00"), account.getCreditLimit());
        assertEquals(new BigDecimal("10000.00"), account.getAvailableCredit());
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should allow withdrawals up to credit limit")
    void shouldAllowWithdrawalsUpToCreditLimit() {
        // Create credit account
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("1000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // Withdraw within credit limit
        CreateTransactionRequest withdrawRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("500.00"))
                .description("Credit withdrawal")
                .build();
        
        TransactionResponse transaction = transactionService.createTransaction(
                userId, account.getId(), withdrawRequest);
        
        assertNotNull(transaction);
        assertEquals(new BigDecimal("500.00"), transaction.getAmount());
        assertEquals(0, transaction.getBalanceBefore().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("-500.00"), transaction.getBalanceAfter());
        
        // Check updated account
        AccountResponse updatedAccount = accountService.getAccountById(userId, account.getId());
        assertEquals(new BigDecimal("-500.00"), updatedAccount.getBalance());
        assertEquals(new BigDecimal("500.00"), updatedAccount.getAvailableCredit());
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should reject withdrawals exceeding credit limit")
    void shouldRejectWithdrawalsExceedingCreditLimit() {
        // Create credit account with $1000 limit
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("1000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // Try to withdraw more than credit limit
        CreateTransactionRequest withdrawRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1500.00"))
                .description("Excessive withdrawal")
                .build();
        
        assertThrows(InsufficientFundsException.class, () -> 
            transactionService.createTransaction(userId, account.getId(), withdrawRequest)
        );
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should handle deposits to credit account correctly")
    void shouldHandleDepositsToCredit() {
        // Create credit account and use some credit
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("1000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // Use $600 of credit
        CreateTransactionRequest withdrawRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("600.00"))
                .build();
        transactionService.createTransaction(userId, account.getId(), withdrawRequest);
        
        // Make a payment (deposit) of $400
        CreateTransactionRequest depositRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("400.00"))
                .description("Credit card payment")
                .build();
        TransactionResponse deposit = transactionService.createTransaction(
                userId, account.getId(), depositRequest);
        
        assertEquals(new BigDecimal("-600.00"), deposit.getBalanceBefore());
        assertEquals(new BigDecimal("-200.00"), deposit.getBalanceAfter());
        
        // Check updated account
        AccountResponse updatedAccount = accountService.getAccountById(userId, account.getId());
        assertEquals(new BigDecimal("-200.00"), updatedAccount.getBalance());
        assertEquals(new BigDecimal("800.00"), updatedAccount.getAvailableCredit());
    }
    
    @Test
    @WithMockUser(username = "credit.test@example.com")
    @DisplayName("Should calculate available credit correctly after multiple transactions")
    void shouldCalculateAvailableCreditCorrectly() {
        // Create credit account with $2000 limit
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("2000.00"))
                .build();
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        
        // Transaction 1: Withdraw $500
        transactionService.createTransaction(userId, account.getId(),
                CreateTransactionRequest.builder()
                        .transactionType(Transaction.TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("500.00"))
                        .build());
        
        // Transaction 2: Withdraw $300
        transactionService.createTransaction(userId, account.getId(),
                CreateTransactionRequest.builder()
                        .transactionType(Transaction.TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("300.00"))
                        .build());
        
        // Transaction 3: Deposit $200 (payment)
        transactionService.createTransaction(userId, account.getId(),
                CreateTransactionRequest.builder()
                        .transactionType(Transaction.TransactionType.DEPOSIT)
                        .amount(new BigDecimal("200.00"))
                        .build());
        
        // Check final state
        AccountResponse finalAccount = accountService.getAccountById(userId, account.getId());
        assertEquals(new BigDecimal("-600.00"), finalAccount.getBalance()); // -500 - 300 + 200
        assertEquals(new BigDecimal("1400.00"), finalAccount.getAvailableCredit()); // 2000 - 600
    }
}