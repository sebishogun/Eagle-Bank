package com.eaglebank.integration;

import com.eaglebank.dto.response.AccountTransactionSummary;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
class AccountRepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private User testUser;
    private Account checkingAccount;
    private Account savingsAccount;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+1234567890")
                .address("123 Test St")
                .password("$2a$10$test")
                .build();
        testUser = userRepository.save(testUser);

        // Create checking account
        checkingAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("CHK" + System.currentTimeMillis())
                .accountName("Test Checking Account")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(testUser)
                .build();
        checkingAccount = accountRepository.save(checkingAccount);

        // Create savings account
        savingsAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("SAV" + System.currentTimeMillis())
                .accountName("Test Savings Account")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(testUser)
                .build();
        savingsAccount = accountRepository.save(savingsAccount);
    }

    @Test
    @DisplayName("Should find accounts by minimum transaction amount")
    void findAccountsByMinimumTransactionAmount() {
        // Create transactions
        createTransaction(checkingAccount, new BigDecimal("100.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(checkingAccount, new BigDecimal("500.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(savingsAccount, new BigDecimal("50.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(savingsAccount, new BigDecimal("1500.00"), Transaction.TransactionType.DEPOSIT);

        // Test finding accounts with transactions >= $500
        List<Account> accounts = accountRepository.findAccountsByMinimumTransactionAmount(new BigDecimal("500.00"));

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::getId)
                .containsExactlyInAnyOrder(checkingAccount.getId(), savingsAccount.getId());

        // Test finding accounts with transactions >= $1000
        accounts = accountRepository.findAccountsByMinimumTransactionAmount(new BigDecimal("1000.00"));

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getId()).isEqualTo(savingsAccount.getId());
    }

    @Test
    @DisplayName("Should find accounts by user and transaction date range")
    void findAccountsByUserAndTransactionDateRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime lastWeek = now.minusDays(7);
        LocalDateTime nextWeek = now.plusDays(7);

        // Create transactions with different dates
        createTransactionWithDate(checkingAccount, new BigDecimal("100.00"), yesterday);
        createTransactionWithDate(checkingAccount, new BigDecimal("200.00"), lastWeek);
        createTransactionWithDate(savingsAccount, new BigDecimal("300.00"), now);

        // Test finding accounts with transactions in the past 3 days
        List<Account> accounts = accountRepository.findAccountsByUserAndTransactionDateRange(
                testUser.getId(), now.minusDays(3), nextWeek);

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::getId)
                .containsExactlyInAnyOrder(checkingAccount.getId(), savingsAccount.getId());

        // Test finding accounts with transactions in the past day only
        accounts = accountRepository.findAccountsByUserAndTransactionDateRange(
                testUser.getId(), now.minusHours(12), nextWeek);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getId()).isEqualTo(savingsAccount.getId());
    }

    @Test
    @DisplayName("Should find active accounts with high value transactions")
    void findActiveAccountsWithHighValueTransactions() {
        // Create a frozen account
        Account frozenAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("FRZ" + System.currentTimeMillis())
                .accountName("Test Frozen Account")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .status(Account.AccountStatus.FROZEN)
                .user(testUser)
                .build();
        frozenAccount = accountRepository.save(frozenAccount);

        // Create transactions
        createTransaction(checkingAccount, new BigDecimal("5000.00"), Transaction.TransactionType.WITHDRAWAL);
        createTransaction(savingsAccount, new BigDecimal("10000.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(frozenAccount, new BigDecimal("15000.00"), Transaction.TransactionType.DEPOSIT);

        // Test finding only ACTIVE accounts with deposits >= $5000
        Page<Account> accountPage = accountRepository.findActiveAccountsWithHighValueTransactions(
                new BigDecimal("5000.00"), 
                Transaction.TransactionType.DEPOSIT,
                PageRequest.of(0, 10));

        assertThat(accountPage.getTotalElements()).isEqualTo(1);
        assertThat(accountPage.getContent().get(0).getId()).isEqualTo(savingsAccount.getId());
    }

    @Test
    @DisplayName("Should find accounts with minimum transaction count")
    void findAccountsWithMinimumTransactionCount() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        // Create multiple transactions for checking account
        for (int i = 0; i < 5; i++) {
            createTransaction(checkingAccount, new BigDecimal("100.00"), Transaction.TransactionType.DEPOSIT);
        }

        // Create only 2 transactions for savings account
        createTransaction(savingsAccount, new BigDecimal("200.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(savingsAccount, new BigDecimal("300.00"), Transaction.TransactionType.WITHDRAWAL);

        // Test finding accounts with at least 3 transactions
        List<Account> accounts = accountRepository.findAccountsWithMinimumTransactionCount(since, 3L);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getId()).isEqualTo(checkingAccount.getId());

        // Test finding accounts with at least 2 transactions
        accounts = accountRepository.findAccountsWithMinimumTransactionCount(since, 2L);

        assertThat(accounts).hasSize(2);
    }

    @Test
    @DisplayName("Should get account transaction summaries for user")
    void getAccountTransactionSummariesForUser() {
        // Create transactions
        createTransaction(checkingAccount, new BigDecimal("100.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(checkingAccount, new BigDecimal("200.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(checkingAccount, new BigDecimal("50.00"), Transaction.TransactionType.WITHDRAWAL);

        createTransaction(savingsAccount, new BigDecimal("1000.00"), Transaction.TransactionType.DEPOSIT);
        createTransaction(savingsAccount, new BigDecimal("500.00"), Transaction.TransactionType.DEPOSIT);

        // Get summaries
        List<AccountTransactionSummary> summaries = accountRepository.getAccountTransactionSummariesForUser(testUser.getId());

        assertThat(summaries).hasSize(2);

        // Verify checking account summary
        AccountTransactionSummary checkingSummary = summaries.stream()
                .filter(s -> s.getAccountId().equals(checkingAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(checkingSummary.getTransactionCount()).isEqualTo(3L);
        assertThat(checkingSummary.getTotalAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(checkingSummary.getAverageAmount()).isCloseTo(new BigDecimal("116.67"), within(new BigDecimal("0.01")));

        // Verify savings account summary
        AccountTransactionSummary savingsSummary = summaries.stream()
                .filter(s -> s.getAccountId().equals(savingsAccount.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(savingsSummary.getTransactionCount()).isEqualTo(2L);
        assertThat(savingsSummary.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(savingsSummary.getAverageAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("Should handle accounts with no transactions in summary")
    void getAccountTransactionSummariesWithNoTransactions() {
        // Create another user with account but no transactions
        User anotherUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("another@example.com")
                .firstName("Another")
                .lastName("User")
                .phoneNumber("+0987654321")
                .address("456 Test Ave")
                .password("$2a$10$test")
                .build();
        anotherUser = userRepository.save(anotherUser);

        Account emptyAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("EMP" + System.currentTimeMillis())
                .accountName("Empty Account")
                .accountType(Account.AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(anotherUser)
                .build();
        accountRepository.save(emptyAccount);

        // Get summaries
        List<AccountTransactionSummary> summaries = accountRepository.getAccountTransactionSummariesForUser(anotherUser.getId());

        assertThat(summaries).hasSize(1);
        AccountTransactionSummary summary = summaries.get(0);
        assertThat(summary.getTransactionCount()).isEqualTo(0L);
        assertThat(summary.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getAverageAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Helper methods
    private Transaction createTransaction(Account account, BigDecimal amount, Transaction.TransactionType type) {
        return createTransactionWithDate(account, amount, LocalDateTime.now(), type);
    }

    private Transaction createTransactionWithDate(Account account, BigDecimal amount, LocalDateTime date) {
        return createTransactionWithDate(account, amount, date, Transaction.TransactionType.DEPOSIT);
    }

    private Transaction createTransactionWithDate(Account account, BigDecimal amount, LocalDateTime date, Transaction.TransactionType type) {
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .type(type)
                .amount(amount)
                .balanceAfter(account.getBalance().add(type == Transaction.TransactionType.DEPOSIT ? amount : amount.negate()))
                .referenceNumber("TXN" + System.currentTimeMillis())
                .transactionDate(date)
                .status(Transaction.TransactionStatus.COMPLETED)
                .account(account)
                .build();
        
        return transactionRepository.save(transaction);
    }
}