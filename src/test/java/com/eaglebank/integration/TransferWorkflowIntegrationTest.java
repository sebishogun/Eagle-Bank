package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransferRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.TransferResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.TransactionService;
import com.eaglebank.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import com.eaglebank.config.TestStrategyConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class TransferWorkflowIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    private List<UUID> userIds = new ArrayList<>();
    private List<UUID> accountIds = new ArrayList<>();
    
    @BeforeEach
    void setUp() {
        // Use timestamp to ensure unique emails
        long timestamp = System.currentTimeMillis();
        
        // Create 3 users for complex transfer scenarios
        for (int i = 1; i <= 3; i++) {
            CreateUserRequest userRequest = CreateUserRequest.builder()
                    .email("workflow_user" + i + "_" + timestamp + "@example.com")
                    .password("Password" + i + "23!")
                    .firstName("Workflow")
                    .lastName("User" + i)
                    .phoneNumber("+123456789" + i)
                    .address(i + " Test Street")
                    .build();
            
            UserResponse user = userService.createUser(userRequest);
            userIds.add(user.getId());
            
            // Create account for each user with different types
            // Note: CREDIT accounts always start with 0 balance regardless of initialBalance
            AccountType accountType = switch (i) {
                case 1 -> AccountType.CHECKING;
                case 2 -> AccountType.SAVINGS;
                default -> AccountType.CHECKING; // Changed from CREDIT to CHECKING for predictable balance
            };
            
            CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                    .accountType(accountType)
                    .accountName("Account " + i)
                    .initialBalance(new BigDecimal("1000.00").multiply(new BigDecimal(i)))
                    .build();
            
            AccountResponse account = accountService.createAccount(user.getId(), accountRequest);
            accountIds.add(account.getId());
        }
    }
    
    @Test
    @DisplayName("Should complete multi-step transfer chain successfully")
    void shouldCompleteMultiStepTransferChain() {
        // Initial balances: Account1=1000, Account2=2000, Account3=3000
        
        // Transfer 1: User1 -> User2 (500)
        CreateTransferRequest transfer1 = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(0))
                .targetAccountId(accountIds.get(1))
                .amount(new BigDecimal("500.00"))
                .description("Chain transfer 1")
                .build();
        
        TransferResponse response1 = transactionService.createTransfer(userIds.get(0), transfer1);
        assertEquals("COMPLETED", response1.getStatus());
        
        // Transfer 2: User2 -> User3 (750)
        CreateTransferRequest transfer2 = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(1))
                .targetAccountId(accountIds.get(2))
                .amount(new BigDecimal("750.00"))
                .description("Chain transfer 2")
                .build();
        
        TransferResponse response2 = transactionService.createTransfer(userIds.get(1), transfer2);
        assertEquals("COMPLETED", response2.getStatus());
        
        // Transfer 3: User3 -> User1 (250) - completing the circle
        CreateTransferRequest transfer3 = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(2))
                .targetAccountId(accountIds.get(0))
                .amount(new BigDecimal("250.00"))
                .description("Chain transfer 3")
                .build();
        
        TransferResponse response3 = transactionService.createTransfer(userIds.get(2), transfer3);
        assertEquals("COMPLETED", response3.getStatus());
        
        // Verify final balances
        // Account1: 1000 - 500 + 250 = 750
        // Account2: 2000 + 500 - 750 = 1750
        // Account3: 3000 + 750 - 250 = 3500
        
        AccountResponse account1 = accountService.getAccountById(userIds.get(0), accountIds.get(0));
        AccountResponse account2 = accountService.getAccountById(userIds.get(1), accountIds.get(1));
        AccountResponse account3 = accountService.getAccountById(userIds.get(2), accountIds.get(2));
        
        assertEquals(new BigDecimal("750.00"), account1.getBalance());
        assertEquals(new BigDecimal("1750.00"), account2.getBalance());
        assertEquals(new BigDecimal("3500.00"), account3.getBalance());
    }
    
    @Test
    @DisplayName("Should handle account lifecycle with transfers")
    void shouldHandleAccountLifecycleWithTransfers() {
        UUID userId = userIds.get(0);
        UUID accountId = accountIds.get(0);
        UUID targetAccountId = accountIds.get(1);
        
        // 1. Perform initial transfer while account is ACTIVE
        CreateTransferRequest transfer1 = CreateTransferRequest.builder()
                .sourceAccountId(accountId)
                .targetAccountId(targetAccountId)
                .amount(new BigDecimal("100.00"))
                .description("Active transfer")
                .build();
        
        TransferResponse response1 = transactionService.createTransfer(userId, transfer1);
        assertEquals("COMPLETED", response1.getStatus());
        
        // 2. Freeze the account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Security review")
                .build();
        
        AccountResponse frozenAccount = accountService.updateAccount(userId, accountId, freezeRequest);
        assertEquals(Account.AccountStatus.FROZEN, frozenAccount.getStatus());
        
        // 3. Attempt outgoing transfer from frozen account (should fail)
        CreateTransferRequest transfer2 = CreateTransferRequest.builder()
                .sourceAccountId(accountId)
                .targetAccountId(targetAccountId)
                .amount(new BigDecimal("50.00"))
                .description("Frozen transfer attempt")
                .build();
        
        assertThrows(IllegalStateException.class, () -> 
            transactionService.createTransfer(userId, transfer2)
        );
        
        // 4. Receive transfer to frozen account (should succeed)
        CreateTransferRequest transfer3 = CreateTransferRequest.builder()
                .sourceAccountId(targetAccountId)
                .targetAccountId(accountId)
                .amount(new BigDecimal("200.00"))
                .description("Payment to frozen account")
                .build();
        
        TransferResponse response3 = transactionService.createTransfer(userIds.get(1), transfer3);
        assertEquals("COMPLETED", response3.getStatus());
        
        // 5. Reactivate the account
        UpdateAccountRequest reactivateRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.ACTIVE)
                .statusChangeReason("Review completed")
                .build();
        
        AccountResponse activeAccount = accountService.updateAccount(userId, accountId, reactivateRequest);
        assertEquals(Account.AccountStatus.ACTIVE, activeAccount.getStatus());
        
        // 6. Transfer should work again
        CreateTransferRequest transfer4 = CreateTransferRequest.builder()
                .sourceAccountId(accountId)
                .targetAccountId(targetAccountId)
                .amount(new BigDecimal("75.00"))
                .description("Reactivated transfer")
                .build();
        
        TransferResponse response4 = transactionService.createTransfer(userId, transfer4);
        assertEquals("COMPLETED", response4.getStatus());
        
        // Verify final balance: 1000 - 100 + 200 - 75 = 1025
        AccountResponse finalAccount = accountService.getAccountById(userId, accountId);
        assertEquals(new BigDecimal("1025.00"), finalAccount.getBalance());
    }
    
    @Test
    @DisplayName("Should handle cross-account-type transfers")
    void shouldHandleCrossAccountTypeTransfers() {
        // Account types: CHECKING (1000), SAVINGS (2000), CHECKING (3000)
        // Note: Changed from CREDIT to CHECKING as CREDIT accounts start with 0 balance
        
        // First CHECKING to SAVINGS
        CreateTransferRequest checkingToSavings = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(0))  // CHECKING
                .targetAccountId(accountIds.get(1))  // SAVINGS
                .amount(new BigDecimal("200.00"))
                .description("Checking to Savings")
                .build();
        
        TransferResponse response1 = transactionService.createTransfer(userIds.get(0), checkingToSavings);
        assertEquals("COMPLETED", response1.getStatus());
        
        // SAVINGS to Second CHECKING
        CreateTransferRequest savingsToChecking2 = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(1))  // SAVINGS
                .targetAccountId(accountIds.get(2))  // CHECKING
                .amount(new BigDecimal("300.00"))
                .description("Savings to Checking")
                .build();
        
        TransferResponse response2 = transactionService.createTransfer(userIds.get(1), savingsToChecking2);
        assertEquals("COMPLETED", response2.getStatus());
        
        // Second CHECKING to First CHECKING
        CreateTransferRequest checking2ToChecking1 = CreateTransferRequest.builder()
                .sourceAccountId(accountIds.get(2))  // CHECKING
                .targetAccountId(accountIds.get(0))  // CHECKING
                .amount(new BigDecimal("150.00"))
                .description("Checking to Checking")
                .build();
        
        TransferResponse response3 = transactionService.createTransfer(userIds.get(2), checking2ToChecking1);
        assertEquals("COMPLETED", response3.getStatus());
        
        // Verify all transfers completed successfully
        AccountResponse checking1 = accountService.getAccountById(userIds.get(0), accountIds.get(0));
        AccountResponse savings = accountService.getAccountById(userIds.get(1), accountIds.get(1));
        AccountResponse checking2 = accountService.getAccountById(userIds.get(2), accountIds.get(2));
        
        assertEquals(new BigDecimal("950.00"), checking1.getBalance());   // 1000 - 200 + 150
        assertEquals(new BigDecimal("1900.00"), savings.getBalance());     // 2000 + 200 - 300
        assertEquals(new BigDecimal("3150.00"), checking2.getBalance());   // 3000 + 300 - 150
    }
    
    @Test
    @DisplayName("Should maintain consistency with concurrent transfers")
    void shouldMaintainConsistencyWithConcurrentTransfers() throws Exception {
        UUID sourceAccountId = accountIds.get(0);
        UUID targetAccountId = accountIds.get(1);
        UUID userId = userIds.get(0);
        
        // Initial balance: 1000
        int numberOfTransfers = 10;
        BigDecimal transferAmount = new BigDecimal("10.00");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<TransferResponse>> futures = new ArrayList<>();
        
        for (int i = 0; i < numberOfTransfers; i++) {
            final int index = i;
            CompletableFuture<TransferResponse> future = CompletableFuture.supplyAsync(() -> {
                CreateTransferRequest request = CreateTransferRequest.builder()
                        .sourceAccountId(sourceAccountId)
                        .targetAccountId(targetAccountId)
                        .amount(transferAmount)
                        .description("Concurrent transfer " + index)
                        .build();
                
                try {
                    return transactionService.createTransfer(userId, request);
                } catch (Exception e) {
                    // Some transfers might fail due to insufficient funds
                    return null;
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all transfers to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        // Count successful transfers
        long successfulTransfers = futures.stream()
                .map(CompletableFuture::join)
                .filter(response -> response != null && "COMPLETED".equals(response.getStatus()))
                .count();
        
        // Verify final balances are consistent
        AccountResponse sourceAccount = accountService.getAccountById(userId, sourceAccountId);
        AccountResponse targetAccount = accountService.getAccountById(userIds.get(1), targetAccountId);
        
        BigDecimal expectedSourceBalance = new BigDecimal("1000.00")
                .subtract(transferAmount.multiply(new BigDecimal(successfulTransfers)));
        BigDecimal expectedTargetBalance = new BigDecimal("2000.00")
                .add(transferAmount.multiply(new BigDecimal(successfulTransfers)));
        
        assertEquals(expectedSourceBalance, sourceAccount.getBalance());
        assertEquals(expectedTargetBalance, targetAccount.getBalance());
        
        // Total money in system should remain constant
        BigDecimal totalMoney = sourceAccount.getBalance()
                .add(targetAccount.getBalance())
                .add(accountService.getAccountById(userIds.get(2), accountIds.get(2)).getBalance());
        assertEquals(new BigDecimal("6000.00"), totalMoney);
    }
    
    @Test
    @DisplayName("Should handle edge case transfers correctly")
    void shouldHandleEdgeCaseTransfers() {
        UUID accountId1 = accountIds.get(0);
        UUID accountId2 = accountIds.get(1);
        UUID userId1 = userIds.get(0);
        
        // Transfer entire balance
        CreateTransferRequest fullTransfer = CreateTransferRequest.builder()
                .sourceAccountId(accountId1)
                .targetAccountId(accountId2)
                .amount(new BigDecimal("1000.00"))
                .description("Full balance transfer")
                .build();
        
        TransferResponse response = transactionService.createTransfer(userId1, fullTransfer);
        assertEquals("COMPLETED", response.getStatus());
        
        AccountResponse emptyAccount = accountService.getAccountById(userId1, accountId1);
        assertEquals(BigDecimal.ZERO.setScale(2), emptyAccount.getBalance());
        
        // Try to transfer from empty account
        CreateTransferRequest emptyTransfer = CreateTransferRequest.builder()
                .sourceAccountId(accountId1)
                .targetAccountId(accountId2)
                .amount(new BigDecimal("0.01"))
                .description("Transfer from empty")
                .build();
        
        assertThrows(Exception.class, () -> 
            transactionService.createTransfer(userId1, emptyTransfer)
        );
        
        // Transfer with very small amount
        CreateTransferRequest smallTransfer = CreateTransferRequest.builder()
                .sourceAccountId(accountId2)
                .targetAccountId(accountId1)
                .amount(new BigDecimal("0.01"))
                .description("Penny transfer")
                .build();
        
        TransferResponse smallResponse = transactionService.createTransfer(userIds.get(1), smallTransfer);
        assertEquals("COMPLETED", smallResponse.getStatus());
        
        AccountResponse pennyAccount = accountService.getAccountById(userId1, accountId1);
        assertEquals(new BigDecimal("0.01"), pennyAccount.getBalance());
    }
}