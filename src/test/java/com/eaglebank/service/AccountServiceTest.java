package com.eaglebank.service;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.entity.User;
import com.eaglebank.exception.ResourceAlreadyExistsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.metrics.AccountMetricsCollector;
import com.eaglebank.pattern.factory.AccountFactory;
import com.eaglebank.pattern.factory.AccountFactoryProvider;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.pattern.strategy.AccountStatusStrategy;
import com.eaglebank.pattern.strategy.AccountStatusStrategyFactory;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import com.eaglebank.validation.AccountStatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private AccountFactoryProvider factoryProvider;
    
    @Mock
    private AccountFactory accountFactory;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private AccountMetricsCollector accountMetricsCollector;
    
    @Mock
    private AccountStatusStrategyFactory statusStrategyFactory;
    
    @Mock
    private AccountStatusStrategy accountStatusStrategy;
    
    @Mock
    private AccountStatusTransitionValidator statusTransitionValidator;

    @InjectMocks
    private AccountService accountService;

    private User testUser;
    private Account testAccount;
    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        accountId = UuidGenerator.generateUuidV7();
        
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        testAccount = Account.builder()
                .id(accountId)
                .accountNumber("ACC1234567890")
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(testUser)
                .build();
    }

    @Test
    void createAccount_Success() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(factoryProvider.getFactory(AccountType.CHECKING.name())).thenReturn(accountFactory);
        when(accountFactory.createAccount(any(), any())).thenReturn(testAccount);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountResponse response = accountService.createAccount(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.getUserId()).isEqualTo(userId);
        
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_UserNotFound_ThrowsException() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void createAccount_DuplicateAccountNumber_RetriesGeneration() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(factoryProvider.getFactory(AccountType.CHECKING.name())).thenReturn(accountFactory);
        when(accountFactory.createAccount(any(), any())).thenReturn(testAccount);
        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)  // First attempt - exists
                .thenReturn(false); // Second attempt - doesn't exist
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountResponse response = accountService.createAccount(userId, request);

        assertThat(response).isNotNull();
        verify(accountRepository, times(2)).existsByAccountNumber(anyString());
    }

    @Test
    void getAccountById_Success() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        AccountResponse response = accountService.getAccountById(userId, accountId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getAccountType()).isEqualTo(AccountType.CHECKING);
    }

    @Test
    void getAccountById_AccountNotFound_ThrowsException() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(userId, accountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getAccountById_UnauthorizedAccess_ThrowsException() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> accountService.getAccountById(otherUserId, accountId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this account");
    }

    @Test
    void getUserAccounts_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Account> accountPage = new PageImpl<>(List.of(testAccount));
        
        when(accountRepository.findByUserId(userId, pageable)).thenReturn(accountPage);

        Page<AccountResponse> response = accountService.getUserAccounts(userId, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(accountId);
    }

    @Test
    void updateAccount_Success() {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .build();

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountStatusStrategy.canUpdate(testAccount)).thenReturn(true);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountResponse response = accountService.updateAccount(userId, accountId, request);

        assertThat(response).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void updateAccount_AccountNotFound_ThrowsException() {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .build();

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccount(userId, accountId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void updateAccount_UnauthorizedAccess_ThrowsException() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .build();

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> accountService.updateAccount(otherUserId, accountId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this account");
    }

    @Test
    void deleteAccount_Success() {
        testAccount.setBalance(BigDecimal.ZERO); // Must have zero balance to delete
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountStatusStrategy.canDelete(testAccount)).thenReturn(true);
        when(accountRepository.countTransactionsByAccountId(accountId)).thenReturn(0L);

        accountService.deleteAccount(userId, accountId);

        verify(accountRepository).delete(testAccount);
    }

    @Test
    void deleteAccount_WithTransactions_ThrowsException() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountStatusStrategy.canDelete(testAccount)).thenReturn(true);
        when(accountRepository.countTransactionsByAccountId(accountId)).thenReturn(5L);

        assertThatThrownBy(() -> accountService.deleteAccount(userId, accountId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete account with transaction history");
    }

    @Test
    void deleteAccount_UnauthorizedAccess_ThrowsException() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> accountService.deleteAccount(otherUserId, accountId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this account");
    }

    @Test
    void getAccountByAccountNumber_Success() {
        when(accountRepository.findByAccountNumber("ACC1234567890")).thenReturn(Optional.of(testAccount));

        AccountResponse response = accountService.getAccountByAccountNumber(userId, "ACC1234567890");

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("ACC1234567890");
    }

    @Test
    void getAccountByAccountNumber_NotFound_ThrowsException() {
        when(accountRepository.findByAccountNumber("ACC1234567890")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountByAccountNumber(userId, "ACC1234567890"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }
    
    @Test
    void updateAccount_StatusChange_Success() {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Customer request")
                .build();

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        AccountResponse response = accountService.updateAccount(userId, accountId, request);

        assertThat(response).isNotNull();
        verify(statusTransitionValidator).validateTransition(testAccount, Account.AccountStatus.FROZEN, "Customer request");
        verify(eventPublisher).publishAccountStatusChanged(any(Account.class), eq("Customer request"));
        verify(accountRepository).save(any(Account.class));
    }
    
    @Test
    void updateAccount_FrozenAccountCannotUpdate_ThrowsException() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountName("New Name")
                .build();

        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountStatusStrategy.canUpdate(testAccount)).thenReturn(false);
        when(accountStatusStrategy.getRestrictionReason()).thenReturn("Account is frozen. Please contact customer service.");

        assertThatThrownBy(() -> accountService.updateAccount(userId, accountId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account is frozen. Please contact customer service.");
    }
    
    @Test
    void deleteAccount_NonZeroBalance_ThrowsException() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(statusStrategyFactory.getStrategy(testAccount)).thenReturn(accountStatusStrategy);
        when(accountStatusStrategy.canDelete(testAccount)).thenReturn(false);
        when(accountStatusStrategy.getRestrictionReason()).thenReturn("Account must have zero balance to be deleted");

        assertThatThrownBy(() -> accountService.deleteAccount(userId, accountId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account must have zero balance to be deleted");
    }
}