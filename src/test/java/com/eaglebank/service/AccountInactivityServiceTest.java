package com.eaglebank.service;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.service.AccountInactivityService.AccountActivityStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountInactivityServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AccountInactivityService accountInactivityService;

    private User testUser;
    private Account activeAccount;
    private Account inactiveAccount;
    private Transaction recentTransaction;
    private Transaction oldTransaction;

    @BeforeEach
    void setUp() {
        // Set inactivity threshold to 180 days
        ReflectionTestUtils.setField(accountInactivityService, "inactivityDays", 180);
        ReflectionTestUtils.setField(accountInactivityService, "batchSize", 100);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        activeAccount = new Account();
        activeAccount.setId(UUID.randomUUID());
        activeAccount.setAccountNumber("ACC001");
        activeAccount.setUser(testUser);
        activeAccount.setStatus(Account.AccountStatus.ACTIVE);
        activeAccount.setBalance(BigDecimal.valueOf(1000));
        activeAccount.setCreatedAt(LocalDateTime.now().minusDays(200));

        inactiveAccount = new Account();
        inactiveAccount.setId(UUID.randomUUID());
        inactiveAccount.setAccountNumber("ACC002");
        inactiveAccount.setUser(testUser);
        inactiveAccount.setStatus(Account.AccountStatus.ACTIVE);
        inactiveAccount.setBalance(BigDecimal.valueOf(500));
        inactiveAccount.setCreatedAt(LocalDateTime.now().minusDays(200));

        recentTransaction = new Transaction();
        recentTransaction.setId(UUID.randomUUID());
        recentTransaction.setAccount(activeAccount);
        recentTransaction.setTransactionDate(LocalDateTime.now().minusDays(30));
        recentTransaction.setType(Transaction.TransactionType.DEPOSIT);
        recentTransaction.setAmount(BigDecimal.valueOf(100));

        oldTransaction = new Transaction();
        oldTransaction.setId(UUID.randomUUID());
        oldTransaction.setAccount(inactiveAccount);
        oldTransaction.setTransactionDate(LocalDateTime.now().minusDays(200));
        oldTransaction.setType(Transaction.TransactionType.DEPOSIT);
        oldTransaction.setAmount(BigDecimal.valueOf(50));
    }

    @Test
    void checkAndMarkInactiveAccounts_ShouldMarkInactiveAccountsOnly() {
        // Arrange
        Page<Account> accountPage = new PageImpl<>(Arrays.asList(activeAccount, inactiveAccount));
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(accountPage);
        
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(activeAccount.getId()))
                .thenReturn(Optional.of(recentTransaction));
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(inactiveAccount.getId()))
                .thenReturn(Optional.of(oldTransaction));

        // Act
        int markedInactive = accountInactivityService.checkAndMarkInactiveAccounts();

        // Assert
        assertThat(markedInactive).isEqualTo(1);
        verify(accountRepository).save(argThat(account -> 
            account.getId().equals(inactiveAccount.getId()) && 
            account.getStatus() == Account.AccountStatus.INACTIVE
        ));
        verify(eventPublisher).publishAccountStatusChanged(eq(inactiveAccount), anyString());
        verify(accountRepository, never()).save(argThat(account -> 
            account.getId().equals(activeAccount.getId())
        ));
    }

    @Test
    void checkAndMarkInactiveAccounts_ShouldHandleAccountsWithNoTransactions() {
        // Arrange
        Account newInactiveAccount = new Account();
        newInactiveAccount.setId(UUID.randomUUID());
        newInactiveAccount.setAccountNumber("ACC003");
        newInactiveAccount.setStatus(Account.AccountStatus.ACTIVE);
        newInactiveAccount.setCreatedAt(LocalDateTime.now().minusDays(200));

        Page<Account> accountPage = new PageImpl<>(Collections.singletonList(newInactiveAccount));
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(accountPage);
        
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(newInactiveAccount.getId()))
                .thenReturn(Optional.empty());

        // Act
        int markedInactive = accountInactivityService.checkAndMarkInactiveAccounts();

        // Assert
        assertThat(markedInactive).isEqualTo(1);
        verify(accountRepository).save(argThat(account -> 
            account.getId().equals(newInactiveAccount.getId()) && 
            account.getStatus() == Account.AccountStatus.INACTIVE
        ));
    }

    @Test
    void checkAndMarkInactiveAccounts_ShouldProcessMultipleBatches() {
        // Arrange
        ReflectionTestUtils.setField(accountInactivityService, "batchSize", 2);
        
        Account account3 = new Account();
        account3.setId(UUID.randomUUID());
        account3.setStatus(Account.AccountStatus.ACTIVE);
        account3.setCreatedAt(LocalDateTime.now().minusDays(200));
        
        Page<Account> page1 = new PageImpl<>(Arrays.asList(activeAccount, inactiveAccount), PageRequest.of(0, 2), 3);
        Page<Account> page2 = new PageImpl<>(Collections.singletonList(account3), PageRequest.of(1, 2), 3);
        
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), eq(PageRequest.of(0, 2))))
                .thenReturn(page1);
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), eq(PageRequest.of(1, 2))))
                .thenReturn(page2);
        
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(any()))
                .thenReturn(Optional.empty());

        // Act
        int markedInactive = accountInactivityService.checkAndMarkInactiveAccounts();

        // Assert
        assertThat(markedInactive).isEqualTo(3);
        verify(accountRepository, times(2)).findByStatus(eq(Account.AccountStatus.ACTIVE), any(PageRequest.class));
    }

    @Test
    void isAccountInactive_ShouldReturnTrueForInactiveAccount() {
        // Arrange
        when(accountRepository.findById(inactiveAccount.getId()))
                .thenReturn(Optional.of(inactiveAccount));
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(inactiveAccount.getId()))
                .thenReturn(Optional.of(oldTransaction));

        // Act
        boolean isInactive = accountInactivityService.isAccountInactive(inactiveAccount.getId());

        // Assert
        assertThat(isInactive).isTrue();
    }

    @Test
    void isAccountInactive_ShouldReturnFalseForActiveAccount() {
        // Arrange
        when(accountRepository.findById(activeAccount.getId()))
                .thenReturn(Optional.of(activeAccount));
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(activeAccount.getId()))
                .thenReturn(Optional.of(recentTransaction));

        // Act
        boolean isInactive = accountInactivityService.isAccountInactive(activeAccount.getId());

        // Assert
        assertThat(isInactive).isFalse();
    }

    @Test
    void isAccountInactive_ShouldReturnFalseForNonActiveStatus() {
        // Arrange
        Account frozenAccount = new Account();
        frozenAccount.setId(UUID.randomUUID());
        frozenAccount.setStatus(Account.AccountStatus.FROZEN);
        
        when(accountRepository.findById(frozenAccount.getId()))
                .thenReturn(Optional.of(frozenAccount));

        // Act
        boolean isInactive = accountInactivityService.isAccountInactive(frozenAccount.getId());

        // Assert
        assertThat(isInactive).isFalse();
        verify(transactionRepository, never()).findTopByAccountIdOrderByTransactionDateDesc(any());
    }

    @Test
    void isAccountInactive_ShouldThrowExceptionForNonExistentAccount() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(accountRepository.findById(nonExistentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountInactivityService.isAccountInactive(nonExistentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getActivityStatistics_ShouldReturnCorrectStatistics() {
        // Arrange
        when(accountRepository.count()).thenReturn(100L);
        when(accountRepository.countByStatus(Account.AccountStatus.ACTIVE)).thenReturn(60L);
        when(accountRepository.countByStatus(Account.AccountStatus.INACTIVE)).thenReturn(20L);
        when(accountRepository.countByStatus(Account.AccountStatus.FROZEN)).thenReturn(10L);
        when(accountRepository.countByStatus(Account.AccountStatus.CLOSED)).thenReturn(10L);

        // Act
        AccountActivityStatistics stats = accountInactivityService.getActivityStatistics();

        // Assert
        assertThat(stats.getTotalAccounts()).isEqualTo(100);
        assertThat(stats.getActiveAccounts()).isEqualTo(60);
        assertThat(stats.getInactiveAccounts()).isEqualTo(20);
        assertThat(stats.getFrozenAccounts()).isEqualTo(10);
        assertThat(stats.getClosedAccounts()).isEqualTo(10);
        assertThat(stats.getInactivityThresholdDays()).isEqualTo(180);
    }

    @Test
    void markAccountAsInactive_ShouldUpdateStatusAndPublishEvent() {
        // Arrange
        Page<Account> accountPage = new PageImpl<>(Collections.singletonList(inactiveAccount));
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(accountPage);
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(inactiveAccount.getId()))
                .thenReturn(Optional.of(oldTransaction));

        // Act
        accountInactivityService.checkAndMarkInactiveAccounts();

        // Assert
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(accountRepository).save(accountCaptor.capture());
        verify(eventPublisher).publishAccountStatusChanged(eq(inactiveAccount), reasonCaptor.capture());
        
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(Account.AccountStatus.INACTIVE);
        assertThat(reasonCaptor.getValue()).contains("marked inactive due to no activity for 180 days");
    }

    @Test
    void getInactivityDays_ShouldReturnConfiguredValue() {
        // Assert
        assertThat(accountInactivityService.getInactivityDays()).isEqualTo(180);
    }

    @Test
    void checkAndMarkInactiveAccounts_ShouldNotMarkRecentlyActiveAccounts() {
        // Arrange
        Account recentlyActiveAccount = new Account();
        recentlyActiveAccount.setId(UUID.randomUUID());
        recentlyActiveAccount.setStatus(Account.AccountStatus.ACTIVE);
        recentlyActiveAccount.setCreatedAt(LocalDateTime.now().minusDays(30));
        
        Page<Account> accountPage = new PageImpl<>(Collections.singletonList(recentlyActiveAccount));
        when(accountRepository.findByStatus(eq(Account.AccountStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(accountPage);
        when(transactionRepository.findTopByAccountIdOrderByTransactionDateDesc(recentlyActiveAccount.getId()))
                .thenReturn(Optional.empty());

        // Act
        int markedInactive = accountInactivityService.checkAndMarkInactiveAccounts();

        // Assert
        assertThat(markedInactive).isEqualTo(0);
        verify(accountRepository, never()).save(any());
        verify(eventPublisher, never()).publishAccountStatusChanged(any(), anyString());
    }
}