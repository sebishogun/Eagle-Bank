package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountStatusStrategyTest {
    
    private ActiveAccountStrategy activeStrategy;
    private FrozenAccountStrategy frozenStrategy;
    private ClosedAccountStrategy closedStrategy;
    private Account testAccount;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        activeStrategy = new ActiveAccountStrategy();
        frozenStrategy = new FrozenAccountStrategy();
        closedStrategy = new ClosedAccountStrategy();
        
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        
        testAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("ACC123456")
                .accountName("Test Account")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(testUser)
                .build();
    }
    
    @Test
    @DisplayName("Active account should allow all operations")
    void testActiveAccountOperations() {
        testAccount.setStatus(Account.AccountStatus.ACTIVE);
        
        assertTrue(activeStrategy.canWithdraw(testAccount, new BigDecimal("100")));
        assertTrue(activeStrategy.canDeposit(testAccount, new BigDecimal("100")));
        assertTrue(activeStrategy.canUpdate(testAccount));
        
        // Can delete only with zero balance
        assertFalse(activeStrategy.canDelete(testAccount));
        testAccount.setBalance(BigDecimal.ZERO);
        assertTrue(activeStrategy.canDelete(testAccount));
    }
    
    @Test
    @DisplayName("Active account should allow transitions to FROZEN and CLOSED")
    void testActiveAccountTransitions() {
        testAccount.setStatus(Account.AccountStatus.ACTIVE);
        
        assertTrue(activeStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.FROZEN));
        assertTrue(activeStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.CLOSED));
        assertFalse(activeStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.ACTIVE));
    }
    
    @Test
    @DisplayName("Frozen account should block withdrawals and updates but allow deposits")
    void testFrozenAccountOperations() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        
        assertFalse(frozenStrategy.canWithdraw(testAccount, new BigDecimal("100")));
        assertTrue(frozenStrategy.canDeposit(testAccount, new BigDecimal("100")));
        assertFalse(frozenStrategy.canUpdate(testAccount));
        assertFalse(frozenStrategy.canDelete(testAccount));
    }
    
    @Test
    @DisplayName("Frozen account should allow transitions to ACTIVE and CLOSED")
    void testFrozenAccountTransitions() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        
        assertTrue(frozenStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.ACTIVE));
        assertTrue(frozenStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.CLOSED));
        assertFalse(frozenStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.FROZEN));
    }
    
    @Test
    @DisplayName("Closed account should block all operations except balance inquiry")
    void testClosedAccountOperations() {
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        
        assertFalse(closedStrategy.canWithdraw(testAccount, new BigDecimal("100")));
        assertFalse(closedStrategy.canDeposit(testAccount, new BigDecimal("100")));
        assertFalse(closedStrategy.canUpdate(testAccount));
        
        // Can delete only with zero balance
        assertFalse(closedStrategy.canDelete(testAccount));
        testAccount.setBalance(BigDecimal.ZERO);
        assertTrue(closedStrategy.canDelete(testAccount));
    }
    
    @Test
    @DisplayName("Closed account should not allow any status transitions")
    void testClosedAccountTransitions() {
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        
        assertFalse(closedStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.ACTIVE));
        assertFalse(closedStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.FROZEN));
        assertFalse(closedStrategy.canChangeStatusTo(testAccount, Account.AccountStatus.CLOSED));
    }
    
    @Test
    @DisplayName("Strategies should provide appropriate restriction reasons")
    void testRestrictionReasons() {
        assertEquals("Account must have zero balance to be deleted", activeStrategy.getRestrictionReason());
        assertEquals("Account is frozen. Please contact customer service.", frozenStrategy.getRestrictionReason());
        assertEquals("Account is closed. No operations are allowed.", closedStrategy.getRestrictionReason());
    }
    
    @Test
    @DisplayName("Strategies should return correct handled status")
    void testHandledStatus() {
        assertEquals(Account.AccountStatus.ACTIVE, activeStrategy.getHandledStatus());
        assertEquals(Account.AccountStatus.FROZEN, frozenStrategy.getHandledStatus());
        assertEquals(Account.AccountStatus.CLOSED, closedStrategy.getHandledStatus());
    }
    
    @Test
    @DisplayName("Active account should allow both sending and receiving transfers")
    void testActiveAccountTransfers() {
        testAccount.setStatus(Account.AccountStatus.ACTIVE);
        
        assertTrue(activeStrategy.canTransfer(testAccount, new BigDecimal("100")));
        assertTrue(activeStrategy.canReceiveTransfer(testAccount, new BigDecimal("100")));
    }
    
    @Test
    @DisplayName("Frozen account should block outgoing transfers but allow incoming")
    void testFrozenAccountTransfers() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        
        assertFalse(frozenStrategy.canTransfer(testAccount, new BigDecimal("100")));
        assertTrue(frozenStrategy.canReceiveTransfer(testAccount, new BigDecimal("100")));
    }
    
    @Test
    @DisplayName("Closed account should block all transfers")
    void testClosedAccountTransfers() {
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        
        assertFalse(closedStrategy.canTransfer(testAccount, new BigDecimal("100")));
        assertFalse(closedStrategy.canReceiveTransfer(testAccount, new BigDecimal("100")));
    }
    
    @Test
    @DisplayName("Inactive account should block all transfers")
    void testInactiveAccountTransfers() {
        InactiveAccountStrategy inactiveStrategy = new InactiveAccountStrategy();
        testAccount.setStatus(Account.AccountStatus.INACTIVE);
        
        assertFalse(inactiveStrategy.canTransfer(testAccount, new BigDecimal("100")));
        assertFalse(inactiveStrategy.canReceiveTransfer(testAccount, new BigDecimal("100")));
    }
}