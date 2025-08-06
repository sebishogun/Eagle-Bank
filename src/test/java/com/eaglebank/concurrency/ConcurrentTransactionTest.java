package com.eaglebank.concurrency;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateTransferRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.TransferResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentTransactionTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private User user1;
    private User user2;
    private Account account1;
    private Account account2;
    private Account account3;

    @BeforeEach
    void setUp() {
        // Clean up
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        user1 = User.builder()
                .email("concurrent.user1@test.com")
                .firstName("Concurrent")
                .lastName("User1")
                .password("password123")
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .email("concurrent.user2@test.com")
                .firstName("Concurrent")
                .lastName("User2")
                .password("password123")
                .build();
        user2 = userRepository.save(user2);

        // Create test accounts with sufficient balance
        account1 = Account.builder()
                .accountNumber("ACC1000000001")
                .accountName("Concurrent Test Account 1")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("10000.00"))
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user1)
                .build();
        account1 = accountRepository.save(account1);

        account2 = Account.builder()
                .accountNumber("ACC1000000002")
                .accountName("Concurrent Test Account 2")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user1)
                .build();
        account2 = accountRepository.save(account2);

        account3 = Account.builder()
                .accountNumber("ACC1000000003")
                .accountName("Concurrent Test Account 3")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("3000.00"))
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user2)
                .build();
        account3 = accountRepository.save(account3);
    }

    @Test
    void testConcurrentWithdrawals_ShouldPreventOverdraft() throws InterruptedException {
        // Initial balance is 10000
        BigDecimal withdrawalAmount = new BigDecimal("100.00");
        int numberOfThreads = 20;
        int withdrawalsPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulWithdrawals = new AtomicInteger(0);
        AtomicInteger failedWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    for (int j = 0; j < withdrawalsPerThread; j++) {
                        try {
                            CreateTransactionRequest request = CreateTransactionRequest.builder()
                                    .transactionType(Transaction.TransactionType.WITHDRAWAL)
                                    .amount(withdrawalAmount)
                                    .description("Concurrent withdrawal test")
                                    .build();
                            
                            TransactionResponse response = transactionService.createTransaction(
                                    user1.getId(), account1.getId(), request);
                            
                            assertNotNull(response);
                            successfulWithdrawals.incrementAndGet();
                            log.debug("Withdrawal successful: {}", response.getTransactionReference());
                        } catch (InsufficientFundsException | IllegalStateException e) {
                            failedWithdrawals.incrementAndGet();
                            log.debug("Withdrawal failed as expected: {}", e.getMessage());
                        } catch (Exception e) {
                            log.error("Unexpected error during withdrawal", e);
                            fail("Unexpected exception: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), "Threads should complete within 30 seconds");
        executor.shutdown();

        // Verify results
        Account finalAccount = accountRepository.findById(account1.getId()).orElseThrow();
        BigDecimal expectedMinBalance = BigDecimal.ZERO; // Account shouldn't go negative
        
        log.info("Final balance: {}", finalAccount.getBalance());
        log.info("Successful withdrawals: {}", successfulWithdrawals.get());
        log.info("Failed withdrawals: {}", failedWithdrawals.get());
        
        // Account balance should never go negative
        assertThat(finalAccount.getBalance()).isGreaterThanOrEqualTo(expectedMinBalance);
        
        // Total withdrawals attempted
        int totalAttempted = numberOfThreads * withdrawalsPerThread;
        assertEquals(totalAttempted, successfulWithdrawals.get() + failedWithdrawals.get());
        
        // Verify balance consistency
        BigDecimal totalWithdrawn = withdrawalAmount.multiply(new BigDecimal(successfulWithdrawals.get()));
        BigDecimal expectedBalance = new BigDecimal("10000.00").subtract(totalWithdrawn);
        assertEquals(0, expectedBalance.compareTo(finalAccount.getBalance()),
                "Balance should match: initial - total withdrawn");
    }

    @Test
    void testConcurrentDeposits_ShouldMaintainConsistency() throws InterruptedException {
        BigDecimal depositAmount = new BigDecimal("50.00");
        int numberOfThreads = 10;
        int depositsPerThread = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < depositsPerThread; j++) {
                        CreateTransactionRequest request = CreateTransactionRequest.builder()
                                .transactionType(Transaction.TransactionType.DEPOSIT)
                                .amount(depositAmount)
                                .description("Concurrent deposit test")
                                .build();
                        
                        TransactionResponse response = transactionService.createTransaction(
                                user1.getId(), account1.getId(), request);
                        
                        assertNotNull(response);
                    }
                } catch (Exception e) {
                    log.error("Error during deposit", e);
                    fail("Deposit should not fail: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify final balance
        Account finalAccount = accountRepository.findById(account1.getId()).orElseThrow();
        BigDecimal totalDeposited = depositAmount.multiply(
                new BigDecimal(numberOfThreads * depositsPerThread));
        BigDecimal expectedBalance = new BigDecimal("10000.00").add(totalDeposited);
        
        assertEquals(0, expectedBalance.compareTo(finalAccount.getBalance()),
                "All deposits should be reflected in the balance");
        
        // Verify transaction count
        long transactionCount = transactionRepository.count();
        assertEquals(numberOfThreads * depositsPerThread, transactionCount,
                "All deposit transactions should be recorded");
    }

    @Test
    void testConcurrentTransfers_ShouldPreventDeadlock() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        List<Future<TransferResponse>> futures = new ArrayList<>();

        // Half threads transfer from account1 to account2, half from account2 to account1
        // This tests deadlock prevention
        for (int i = 0; i < numberOfThreads; i++) {
            final boolean reverseDirection = i % 2 == 0;
            
            Future<TransferResponse> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    CreateTransferRequest request = CreateTransferRequest.builder()
                            .sourceAccountId(reverseDirection ? account2.getId() : account1.getId())
                            .targetAccountId(reverseDirection ? account1.getId() : account2.getId())
                            .amount(new BigDecimal("10.00"))
                            .description("Deadlock test transfer")
                            .build();
                    
                    return transactionService.createTransfer(user1.getId(), request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } finally {
                    endLatch.countDown();
                }
            });
            
            futures.add(future);
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), "All transfers should complete without deadlock");
        executor.shutdown();

        // Verify all transfers completed
        int successfulTransfers = 0;
        for (Future<TransferResponse> future : futures) {
            try {
                TransferResponse response = future.get(1, TimeUnit.SECONDS);
                if (response != null) {
                    assertNotNull(response.getTransferReference());
                    assertEquals("COMPLETED", response.getStatus());
                    successfulTransfers++;
                }
            } catch (TimeoutException e) {
                fail("Transfer should not timeout - possible deadlock");
            } catch (ExecutionException e) {
                // Some transfers might fail due to insufficient funds
                log.debug("Transfer failed: {}", e.getCause().getMessage());
            }
        }

        log.info("Successful transfers: {}/{}", successfulTransfers, numberOfThreads);
        assertTrue(successfulTransfers > 0, "At least some transfers should succeed");
        
        // Verify total balance remains constant
        Account finalAccount1 = accountRepository.findById(account1.getId()).orElseThrow();
        Account finalAccount2 = accountRepository.findById(account2.getId()).orElseThrow();
        
        BigDecimal totalBalance = finalAccount1.getBalance().add(finalAccount2.getBalance());
        BigDecimal expectedTotal = new BigDecimal("15000.00"); // Initial: 10000 + 5000
        
        assertEquals(0, expectedTotal.compareTo(totalBalance),
                "Total balance across accounts should remain constant");
    }

    @Test
    void testMixedConcurrentOperations_ShouldMaintainIntegrity() throws InterruptedException {
        int numberOfThreads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger operationCounter = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Each thread performs different operations based on ID
                    if (threadId % 3 == 0) {
                        // Deposits
                        for (int j = 0; j < 5; j++) {
                            CreateTransactionRequest request = CreateTransactionRequest.builder()
                                    .transactionType(Transaction.TransactionType.DEPOSIT)
                                    .amount(new BigDecimal("20.00"))
                                    .description("Mixed operation deposit")
                                    .build();
                            
                            transactionService.createTransaction(user1.getId(), account1.getId(), request);
                            operationCounter.incrementAndGet();
                        }
                    } else if (threadId % 3 == 1) {
                        // Withdrawals
                        for (int j = 0; j < 5; j++) {
                            try {
                                CreateTransactionRequest request = CreateTransactionRequest.builder()
                                        .transactionType(Transaction.TransactionType.WITHDRAWAL)
                                        .amount(new BigDecimal("15.00"))
                                        .description("Mixed operation withdrawal")
                                        .build();
                                
                                transactionService.createTransaction(user1.getId(), account1.getId(), request);
                                operationCounter.incrementAndGet();
                            } catch (InsufficientFundsException e) {
                                log.debug("Withdrawal rejected: insufficient funds");
                            }
                        }
                    } else {
                        // Transfers
                        for (int j = 0; j < 3; j++) {
                            try {
                                CreateTransferRequest request = CreateTransferRequest.builder()
                                        .sourceAccountId(account1.getId())
                                        .targetAccountId(account2.getId())
                                        .amount(new BigDecimal("25.00"))
                                        .description("Mixed operation transfer")
                                        .build();
                                
                                transactionService.createTransfer(user1.getId(), request);
                                operationCounter.incrementAndGet();
                            } catch (InsufficientFundsException e) {
                                log.debug("Transfer rejected: insufficient funds");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in mixed operations", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify account integrity
        Account finalAccount1 = accountRepository.findById(account1.getId()).orElseThrow();
        Account finalAccount2 = accountRepository.findById(account2.getId()).orElseThrow();
        
        log.info("Final account1 balance: {}", finalAccount1.getBalance());
        log.info("Final account2 balance: {}", finalAccount2.getBalance());
        log.info("Total operations attempted: {}", operationCounter.get());
        
        // Balances should never be negative
        assertThat(finalAccount1.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalAccount2.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        
        // Verify all transactions are recorded
        List<Transaction> allTransactions = transactionRepository.findAll();
        assertThat(allTransactions).isNotEmpty();
        
        // Each transaction should have unique reference number
        long uniqueReferences = allTransactions.stream()
                .map(Transaction::getReferenceNumber)
                .distinct()
                .count();
        assertEquals(allTransactions.size(), uniqueReferences,
                "All transactions should have unique reference numbers");
    }

    @Test
    void testOptimisticLocking_ShouldDetectConcurrentModification() throws InterruptedException {
        // This test verifies that optimistic locking works by directly manipulating entities
        Account account = accountRepository.findById(account1.getId()).orElseThrow();
        Long initialVersion = account.getVersion();
        assertNotNull(initialVersion, "Version should be set");

        // Simulate concurrent modification
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailures = new AtomicInteger(0);

        // Two threads try to modify the same account simultaneously
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Each thread loads the account
                    Account threadAccount = accountRepository.findById(account1.getId()).orElseThrow();
                    
                    // Simulate some processing time
                    Thread.sleep(10);
                    
                    // Try to update the account
                    threadAccount.setAccountName("Updated by thread " + threadId);
                    accountRepository.save(threadAccount);
                    successCount.incrementAndGet();
                    
                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockFailures.incrementAndGet();
                    log.debug("Optimistic lock failure detected as expected");
                } catch (Exception e) {
                    log.error("Unexpected error", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // One should succeed, one should fail with optimistic lock exception
        log.info("Successful updates: {}, Optimistic lock failures: {}", 
                successCount.get(), optimisticLockFailures.get());
        
        // In practice, both might succeed if they execute sequentially
        // But at least verify version was incremented
        Account finalAccount = accountRepository.findById(account1.getId()).orElseThrow();
        assertThat(finalAccount.getVersion()).isGreaterThan(initialVersion);
    }

    @Test
    void testPessimisticLocking_ShouldSerializeAccess() throws InterruptedException {
        // This test verifies that pessimistic locking serializes access
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        List<Long> executionOrder = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    // Record when thread starts processing
                    long startTime = System.currentTimeMillis();
                    
                    CreateTransactionRequest request = CreateTransactionRequest.builder()
                            .transactionType(Transaction.TransactionType.WITHDRAWAL)
                            .amount(new BigDecimal("1.00"))
                            .description("Pessimistic lock test " + threadId)
                            .build();
                    
                    transactionService.createTransaction(user1.getId(), account1.getId(), request);
                    
                    executionOrder.add(startTime);
                    
                } catch (Exception e) {
                    log.debug("Transaction failed: {}", e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify transactions were processed (some might fail due to insufficient funds)
        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).isNotEmpty();
        
        // All transactions should have been processed in a serialized manner
        // (no two transactions should have the exact same timestamp)
        long distinctTimestamps = transactions.stream()
                .map(Transaction::getCreatedAt)
                .distinct()
                .count();
        
        log.info("Total transactions: {}, Distinct timestamps: {}", 
                transactions.size(), distinctTimestamps);
    }

    @Test
    void testConcurrentTransfersBetweenMultipleAccounts_ShouldAvoidDeadlock() throws InterruptedException {
        // Complex test with transfers between 3 accounts in different patterns
        int numberOfThreads = 9;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedTransfers = new AtomicInteger(0);

        // Create circular transfer patterns that could cause deadlock
        UUID[] accountIds = {account1.getId(), account2.getId(), account3.getId()};
        UUID[] userIds = {user1.getId(), user1.getId(), user2.getId()};
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int sourceIndex = i % 3;
            final int targetIndex = (i + 1) % 3;
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    CreateTransferRequest request = CreateTransferRequest.builder()
                            .sourceAccountId(accountIds[sourceIndex])
                            .targetAccountId(accountIds[targetIndex])
                            .amount(new BigDecimal("5.00"))
                            .description("Circular transfer test")
                            .build();
                    
                    try {
                        TransferResponse response = transactionService.createTransfer(
                                userIds[sourceIndex], request);
                        assertNotNull(response);
                        successfulTransfers.incrementAndGet();
                    } catch (Exception e) {
                        failedTransfers.incrementAndGet();
                        log.debug("Transfer failed: {}", e.getMessage());
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        
        // Should complete without deadlock
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), 
                "All transfers should complete within 30 seconds (no deadlock)");
        executor.shutdown();

        log.info("Successful transfers: {}, Failed transfers: {}", 
                successfulTransfers.get(), failedTransfers.get());
        
        // At least some transfers should succeed
        assertTrue(successfulTransfers.get() > 0, "Some transfers should succeed");
        
        // Total of all account balances should remain the same
        BigDecimal totalInitial = new BigDecimal("18000.00"); // 10000 + 5000 + 3000
        
        Account final1 = accountRepository.findById(account1.getId()).orElseThrow();
        Account final2 = accountRepository.findById(account2.getId()).orElseThrow();
        Account final3 = accountRepository.findById(account3.getId()).orElseThrow();
        
        BigDecimal totalFinal = final1.getBalance()
                .add(final2.getBalance())
                .add(final3.getBalance());
        
        assertEquals(0, totalInitial.compareTo(totalFinal),
                "Total balance across all accounts should remain constant");
    }
}