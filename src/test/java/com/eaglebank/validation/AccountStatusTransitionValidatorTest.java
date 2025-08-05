package com.eaglebank.validation;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import com.eaglebank.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountStatusTransitionValidatorTest {
    
    private AccountStatusTransitionValidator validator;
    private Account testAccount;
    
    @BeforeEach
    void setUp() {
        validator = new AccountStatusTransitionValidator();
        
        User testUser = User.builder()
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
    @DisplayName("Should allow transition from ACTIVE to FROZEN with reason")
    void validateTransition_ActiveToFrozen_WithReason_Success() {
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, "Customer request");
        });
    }
    
    @Test
    @DisplayName("Should allow transition from ACTIVE to CLOSED with reason and zero balance")
    void validateTransition_ActiveToClosed_WithReasonAndZeroBalance_Success() {
        testAccount.setBalance(BigDecimal.ZERO);
        
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.CLOSED, "Account closure requested");
        });
    }
    
    @Test
    @DisplayName("Should reject transition from ACTIVE to CLOSED with non-zero balance")
    void validateTransition_ActiveToClosed_WithNonZeroBalance_ThrowsException() {
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.CLOSED, "Account closure requested");
        });
    }
    
    @Test
    @DisplayName("Should reject transition from ACTIVE to FROZEN without reason")
    void validateTransition_ActiveToFrozen_NoReason_ThrowsException() {
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, null);
        });
        
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, "");
        });
        
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, "   ");
        });
    }
    
    @Test
    @DisplayName("Should allow transition from FROZEN to ACTIVE with reason")
    void validateTransition_FrozenToActive_WithReason_Success() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.ACTIVE, "Unfreezing account");
        });
    }
    
    @Test
    @DisplayName("Should allow transition from FROZEN to CLOSED with reason and zero balance")
    void validateTransition_FrozenToClosed_WithReasonAndZeroBalance_Success() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        testAccount.setBalance(BigDecimal.ZERO);
        
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.CLOSED, "Closing frozen account");
        });
    }
    
    @Test
    @DisplayName("Should reject transition from CLOSED to any status")
    void validateTransition_ClosedToAny_ThrowsException() {
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.ACTIVE, "Trying to reopen");
        });
        
        assertThrows(InvalidStateTransitionException.class, () -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, "Trying to freeze closed account");
        });
    }
    
    @Test
    @DisplayName("Should allow transition to same status (no-op)")
    void validateTransition_SameStatus_NoOp() {
        // The validator returns early without throwing exception for same status
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.ACTIVE, "No change");
        });
        
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        assertDoesNotThrow(() -> {
            validator.validateTransition(testAccount, Account.AccountStatus.FROZEN, "Already frozen");
        });
    }
    
    @Test
    @DisplayName("Should check if transition is allowed")
    void isTransitionAllowed_ValidTransitions_ReturnsTrue() {
        // ACTIVE can go to FROZEN or CLOSED
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.ACTIVE, Account.AccountStatus.FROZEN));
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.ACTIVE, Account.AccountStatus.CLOSED));
        
        // FROZEN can go to ACTIVE or CLOSED
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.FROZEN, Account.AccountStatus.ACTIVE));
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.FROZEN, Account.AccountStatus.CLOSED));
    }
    
    @Test
    @DisplayName("Should check if transition is not allowed")
    void isTransitionAllowed_InvalidTransitions_ReturnsFalse() {
        // CLOSED cannot go anywhere
        assertFalse(validator.isTransitionAllowed(Account.AccountStatus.CLOSED, Account.AccountStatus.ACTIVE));
        assertFalse(validator.isTransitionAllowed(Account.AccountStatus.CLOSED, Account.AccountStatus.FROZEN));
        
        // Same status transitions are allowed (no-op)
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.ACTIVE, Account.AccountStatus.ACTIVE));
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.FROZEN, Account.AccountStatus.FROZEN));
        assertTrue(validator.isTransitionAllowed(Account.AccountStatus.CLOSED, Account.AccountStatus.CLOSED));
    }
    
    @Test
    @DisplayName("Should handle null account")
    void validateTransition_NullAccount_ThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            validator.validateTransition(null, Account.AccountStatus.FROZEN, "Reason");
        });
    }
    
    @Test
    @DisplayName("Should handle null new status")
    void validateTransition_NullNewStatus_ThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            validator.validateTransition(testAccount, null, "Reason");
        });
    }
}