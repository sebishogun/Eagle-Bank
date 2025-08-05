package com.eaglebank.service;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.Transaction.TransactionStatus;
import com.eaglebank.entity.Transaction.TransactionType;
import com.eaglebank.entity.User;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.metrics.TransactionMetricsCollector;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.pattern.strategy.TransactionStrategy;
import com.eaglebank.pattern.strategy.TransactionStrategyFactory;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.util.UuidGenerator;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private TransactionStrategyFactory strategyFactory;
    
    @Mock
    private TransactionStrategy transactionStrategy;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private TransactionMetricsCollector metricsCollector;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Account testAccount;
    private Transaction testTransaction;
    private UUID userId;
    private UUID accountId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        accountId = UuidGenerator.generateUuidV7();
        transactionId = UuidGenerator.generateUuidV7();
        
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
                .user(testUser)
                .build();

        testTransaction = Transaction.builder()
                .id(transactionId)
                .referenceNumber("TXN" + System.currentTimeMillis())
                .type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .balanceAfter(new BigDecimal("1500.00"))
                .description("Test deposit")
                .status(TransactionStatus.COMPLETED)
                .account(testAccount)
                .transactionDate(LocalDateTime.now())
                .build();
    }

    @Test
    void createDeposit_Success() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Salary deposit")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(strategyFactory.getStrategy(TransactionType.DEPOSIT)).thenReturn(transactionStrategy);
        when(transactionStrategy.calculateNewBalance(testAccount, request.getAmount())).thenReturn(new BigDecimal("1500.00"));
        // Remove unnecessary stubbing - not used in the service
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionResponse response = transactionService.createTransaction(userId, accountId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.getBalanceBefore()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1500.00"));
        
        // Verify account balance was updated
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void createWithdrawal_Success() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("300.00"))
                .description("ATM withdrawal")
                .build();

        testTransaction.setType(TransactionType.WITHDRAWAL);
        testTransaction.setAmount(new BigDecimal("300.00"));
        testTransaction.setBalanceAfter(new BigDecimal("700.00"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(strategyFactory.getStrategy(TransactionType.WITHDRAWAL)).thenReturn(transactionStrategy);
        when(transactionStrategy.calculateNewBalance(testAccount, request.getAmount())).thenReturn(new BigDecimal("700.00"));
        // Remove unnecessary stubbing - not used in the service
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        TransactionResponse response = transactionService.createTransaction(userId, accountId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("700.00"));
        
        // Verify account balance was updated
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void createWithdrawal_InsufficientFunds_ThrowsException() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1500.00"))
                .description("Large withdrawal")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(strategyFactory.getStrategy(TransactionType.WITHDRAWAL)).thenReturn(transactionStrategy);
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(transactionStrategy).validateTransaction(testAccount, request.getAmount());

        assertThatThrownBy(() -> transactionService.createTransaction(userId, accountId, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createTransaction_AccountNotFound_ThrowsException() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(userId, accountId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void createTransaction_UnauthorizedAccess_ThrowsException() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .build();

        UUID otherUserId = UuidGenerator.generateUuidV7();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(otherUserId, accountId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this account");
    }

    @Test
    void getTransactionById_Success() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        TransactionResponse response = transactionService.getTransactionById(userId, accountId, transactionId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(transactionId);
        assertThat(response.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void getTransactionById_NotFound_ThrowsException() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionById(userId, accountId, transactionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void getTransactionById_WrongAccount_ThrowsException() {
        UUID wrongAccountId = UuidGenerator.generateUuidV7();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        assertThatThrownBy(() -> transactionService.getTransactionById(userId, wrongAccountId, transactionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found for this account");
    }

    @Test
    void getTransactionById_UnauthorizedAccess_ThrowsException() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        assertThatThrownBy(() -> transactionService.getTransactionById(otherUserId, accountId, transactionId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this transaction");
    }

    @Test
    void getAccountTransactions_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> transactionPage = new PageImpl<>(List.of(testTransaction));
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
        when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                .thenReturn(transactionPage);

        Page<TransactionResponse> response = transactionService.getAccountTransactions(userId, accountId, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(transactionId);
    }

    @Test
    void getAccountTransactions_AccountNotFound_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getAccountTransactions(userId, accountId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getAccountTransactions_UnauthorizedAccess_ThrowsException() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        Pageable pageable = PageRequest.of(0, 10);
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> transactionService.getAccountTransactions(otherUserId, accountId, pageable))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized to access this account");
    }

    @Test
    void createTransaction_ZeroAmount_ThrowsException() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> transactionService.createTransaction(userId, accountId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
        
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void createTransaction_NegativeAmount_ThrowsException() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("-100.00"))
                .build();

        assertThatThrownBy(() -> transactionService.createTransaction(userId, accountId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
        
        verify(accountRepository, never()).findById(any());
    }
}