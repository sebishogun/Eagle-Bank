package com.eaglebank.integration;

import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.AuditRepository;
import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.InvalidStateTransitionException;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class AccountStatusWorkflowIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private AuditRepository auditRepository;
    
    private UUID userId;
    private UUID accountId;
    
    @BeforeEach
    void setUp() {
        // Create user
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .email("status.test@example.com")
                .password("SecurePass123!")
                .firstName("Status")
                .lastName("Test")
                .build();
        
        UserResponse user = userService.createUser(userRequest);
        userId = user.getId();
        
        // Create account
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialBalance(new BigDecimal("1000.00"))
                .accountName("Test Savings Account")
                .build();
                
        AccountResponse account = accountService.createAccount(userId, accountRequest);
        accountId = account.getId();
    }
    
    @Test
    @DisplayName("Should complete full account lifecycle from ACTIVE to FROZEN to CLOSED")
    void testCompleteAccountLifecycle() {
        // 1. Verify account starts as ACTIVE
        AccountResponse account = accountService.getAccountById(userId, accountId);
        assertEquals(Account.AccountStatus.ACTIVE, account.getStatus());
        
        // 2. Make a deposit while ACTIVE
        CreateTransactionRequest depositRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Salary deposit")
                .build();
                
        TransactionResponse deposit = transactionService.createTransaction(userId, accountId, depositRequest);
        assertEquals(new BigDecimal("1500.00"), deposit.getBalanceAfter());
        
        // 3. Freeze the account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Suspicious activity detected")
                .build();
                
        AccountResponse frozenAccount = accountService.updateAccount(userId, accountId, freezeRequest);
        assertEquals(Account.AccountStatus.FROZEN, frozenAccount.getStatus());
        
        // 4. Verify withdrawal is blocked while FROZEN
        CreateTransactionRequest withdrawalRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("100.00"))
                .description("ATM withdrawal")
                .build();
                
        assertThrows(IllegalStateException.class, () -> {
            transactionService.createTransaction(userId, accountId, withdrawalRequest);
        });
        
        // 5. Verify deposit is still allowed while FROZEN
        CreateTransactionRequest frozenDepositRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("200.00"))
                .description("Refund deposit")
                .build();
                
        TransactionResponse frozenDeposit = transactionService.createTransaction(userId, accountId, frozenDepositRequest);
        assertEquals(new BigDecimal("1700.00"), frozenDeposit.getBalanceAfter());
        
        // 6. Unfreeze the account
        UpdateAccountRequest unfreezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.ACTIVE)
                .statusChangeReason("Investigation completed - no issues found")
                .build();
                
        AccountResponse activeAccount = accountService.updateAccount(userId, accountId, unfreezeRequest);
        assertEquals(Account.AccountStatus.ACTIVE, activeAccount.getStatus());
        
        // 7. Verify withdrawal works again
        TransactionResponse withdrawal = transactionService.createTransaction(userId, accountId, withdrawalRequest);
        assertEquals(new BigDecimal("1600.00"), withdrawal.getBalanceAfter());
        
        // 8. Withdraw all funds to prepare for closure
        CreateTransactionRequest finalWithdrawal = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1600.00"))
                .description("Account closure withdrawal")
                .build();
                
        TransactionResponse finalTx = transactionService.createTransaction(userId, accountId, finalWithdrawal);
        assertEquals(0, finalTx.getBalanceAfter().compareTo(BigDecimal.ZERO));
        
        // 9. Close the account
        UpdateAccountRequest closeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.CLOSED)
                .statusChangeReason("Customer requested account closure")
                .build();
                
        AccountResponse closedAccount = accountService.updateAccount(userId, accountId, closeRequest);
        assertEquals(Account.AccountStatus.CLOSED, closedAccount.getStatus());
        
        // 10. Verify no transactions allowed on CLOSED account
        assertThrows(IllegalStateException.class, () -> {
            transactionService.createTransaction(userId, accountId, depositRequest);
        });
        
        assertThrows(IllegalStateException.class, () -> {
            transactionService.createTransaction(userId, accountId, withdrawalRequest);
        });
        
        // 11. Verify cannot reopen CLOSED account
        UpdateAccountRequest reopenRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.ACTIVE)
                .statusChangeReason("Trying to reopen")
                .build();
                
        assertThrows(InvalidStateTransitionException.class, () -> {
            accountService.updateAccount(userId, accountId, reopenRequest);
        });
    }
    
    @Test
    @DisplayName("Should block account updates while FROZEN")
    void testFrozenAccountCannotBeUpdated() {
        // Freeze the account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Security review")
                .build();
                
        accountService.updateAccount(userId, accountId, freezeRequest);
        
        // Try to update account name while frozen
        UpdateAccountRequest updateRequest = UpdateAccountRequest.builder()
                .accountName("New Account Name")
                .build();
                
        assertThrows(IllegalStateException.class, () -> {
            accountService.updateAccount(userId, accountId, updateRequest);
        });
    }
    
    @Test
    @DisplayName("Should prevent deletion of account with non-zero balance")
    void testCannotDeleteAccountWithBalance() {
        assertThrows(IllegalStateException.class, () -> {
            accountService.deleteAccount(userId, accountId);
        });
        
        // Withdraw all funds
        CreateTransactionRequest withdrawalRequest = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000.00"))
                .description("Full withdrawal")
                .build();
                
        transactionService.createTransaction(userId, accountId, withdrawalRequest);
        
        // Now deletion should fail due to transaction history
        assertThrows(IllegalStateException.class, () -> {
            accountService.deleteAccount(userId, accountId);
        });
    }
    
    @Test
    @DisplayName("Should create audit entries for all status changes")
    void testAuditTrailForStatusChanges() {
        // Initial audit entry count
        long initialCount = auditRepository.count();
        
        // Freeze the account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Compliance check")
                .build();
                
        accountService.updateAccount(userId, accountId, freezeRequest);
        
        // Unfreeze the account
        UpdateAccountRequest unfreezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.ACTIVE)
                .statusChangeReason("Compliance check passed")
                .build();
                
        accountService.updateAccount(userId, accountId, unfreezeRequest);
        
        // Give async audit saving time to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check audit entries
        long finalCount = auditRepository.count();
        assertTrue(finalCount > initialCount, "Expected audit entries to be created");
        
        // Verify audit entries contain UPDATE actions for this account
        List<AuditEntry> allEntries = auditRepository.findAll();
        long updateCount = allEntries.stream()
            .filter(e -> e.getAction() == AuditEntry.AuditAction.UPDATE)
            .filter(e -> accountId.toString().equals(e.getEntityId()))
            .count();
        assertTrue(updateCount >= 2, "Expected at least 2 UPDATE audit entries for status changes");
    }
    
    @Test
    @DisplayName("Should validate status transition reasons")
    void testStatusTransitionReasonValidation() {
        // Try to freeze without reason
        UpdateAccountRequest freezeNoReason = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .build();
                
        assertThrows(InvalidStateTransitionException.class, () -> {
            accountService.updateAccount(userId, accountId, freezeNoReason);
        });
        
        // Try to freeze with empty reason
        UpdateAccountRequest freezeEmptyReason = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("")
                .build();
                
        assertThrows(InvalidStateTransitionException.class, () -> {
            accountService.updateAccount(userId, accountId, freezeEmptyReason);
        });
        
        // Try to freeze with blank reason
        UpdateAccountRequest freezeBlankReason = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("   ")
                .build();
                
        assertThrows(InvalidStateTransitionException.class, () -> {
            accountService.updateAccount(userId, accountId, freezeBlankReason);
        });
    }
    
    @Test
    @DisplayName("Should handle credit account status changes correctly")
    void testCreditAccountStatusWorkflow() {
        // Create credit account
        CreateAccountRequest creditRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CREDIT)
                .initialBalance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("5000.00"))
                .accountName("Test Credit Account")
                .build();
                
        AccountResponse creditAccount = accountService.createAccount(userId, creditRequest);
        
        // Use some credit
        CreateTransactionRequest creditWithdrawal = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000.00"))
                .description("Credit withdrawal")
                .build();
                
        transactionService.createTransaction(userId, creditAccount.getId(), creditWithdrawal);
        
        // Freeze the credit account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Payment overdue")
                .build();
                
        AccountResponse frozenCredit = accountService.updateAccount(userId, creditAccount.getId(), freezeRequest);
        assertEquals(Account.AccountStatus.FROZEN, frozenCredit.getStatus());
        
        // Verify can still make payments (deposits) while frozen
        CreateTransactionRequest payment = CreateTransactionRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Credit payment")
                .build();
                
        TransactionResponse paymentTx = transactionService.createTransaction(userId, creditAccount.getId(), payment);
        assertEquals(new BigDecimal("-500.00"), paymentTx.getBalanceAfter());
    }
}