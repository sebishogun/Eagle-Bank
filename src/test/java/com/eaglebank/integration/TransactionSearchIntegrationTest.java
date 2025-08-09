package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.request.TransactionSearchRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ContextConfiguration;
import com.eaglebank.config.TestStrategyConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionSearchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;
    private UUID accountId;
    private LocalDateTime testStartTime;

    @BeforeEach
    void setUp() throws Exception {
        testStartTime = LocalDateTime.now();
        
        // Create user
        CreateUserRequest userRequest = CreateUserRequest.builder()
                .email("search.test@example.com")
                .password("SecurePass123!")
                .firstName("Search")
                .lastName("Test")
                .phoneNumber("+1234567890")
                .address("123 Search St")
                .build();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("search.test@example.com")
                .password("SecurePass123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), 
                AuthResponse.class);
        jwtToken = authResponse.getToken();

        // Create account
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(Account.AccountType.CHECKING)
                .initialBalance(new BigDecimal("10000.00"))
                .accountName("Test Checking Account")
                .build();

        MvcResult accountResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                accountResult.getResponse().getContentAsString(), 
                AccountResponse.class);
        accountId = account.getId();

        // Create various transactions for testing
        createTestTransactions();
    }

    private void createTestTransactions() throws Exception {
        // Large deposit
        createTransaction(Transaction.TransactionType.DEPOSIT, 
                         new BigDecimal("5000.00"), "Salary payment");
        Thread.sleep(10);
        
        // Small withdrawal
        createTransaction(Transaction.TransactionType.WITHDRAWAL, 
                         new BigDecimal("50.00"), "ATM withdrawal");
        Thread.sleep(10);
        
        // Medium deposit
        createTransaction(Transaction.TransactionType.DEPOSIT, 
                         new BigDecimal("500.00"), "Refund from store");
        Thread.sleep(10);
        
        // Large withdrawal
        createTransaction(Transaction.TransactionType.WITHDRAWAL, 
                         new BigDecimal("2000.00"), "Rent payment");
        Thread.sleep(10);
        
        // Small deposit
        createTransaction(Transaction.TransactionType.DEPOSIT, 
                         new BigDecimal("100.00"), "Cash deposit");
        Thread.sleep(10);
        
        // Medium withdrawal
        createTransaction(Transaction.TransactionType.WITHDRAWAL, 
                         new BigDecimal("300.00"), "Shopping");
    }

    private void createTransaction(Transaction.TransactionType type, 
                                 BigDecimal amount, String description) throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(type)
                .amount(amount)
                .description(description)
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should search transactions by date range")
    void searchTransactionsByDateRange() throws Exception {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .startDate(testStartTime)
                .endDate(endTime)
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(6)))
                .andExpect(jsonPath("$.totalElements", is(6)));
    }

    @Test
    @DisplayName("Should search transactions by amount range")
    void searchTransactionsByAmountRange() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .minAmount(new BigDecimal("100.00"))
                .maxAmount(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3))) // 100, 300, 500
                .andExpect(jsonPath("$.content[*].amount", everyItem(
                        allOf(greaterThanOrEqualTo(100.0), lessThanOrEqualTo(1000.0)))));
    }

    @Test
    @DisplayName("Should search transactions by type")
    void searchTransactionsByType() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3))) // 3 deposits
                .andExpect(jsonPath("$.content[*].transactionType", everyItem(is("DEPOSIT"))));
    }

    @Test
    @DisplayName("Should search transactions by description keyword")
    void searchTransactionsByDescription() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .descriptionKeyword("payment")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))) // Salary payment, Rent payment
                .andExpect(jsonPath("$.content[*].description", 
                        everyItem(containsStringIgnoringCase("payment"))));
    }

    @Test
    @DisplayName("Should search large transactions")
    void searchLargeTransactions() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .largeTransactionThreshold(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))) // 5000 and 2000
                .andExpect(jsonPath("$.content[*].amount", everyItem(greaterThan(1000.0))));
    }

    @Test
    @DisplayName("Should search with multiple filters")
    void searchTransactionsWithMultipleFilters() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .minAmount(new BigDecimal("100.00"))
                .maxAmount(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))) // Only 300 withdrawal
                .andExpect(jsonPath("$.content[0].transactionType", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.content[0].amount", is(300.0)));
    }

    @Test
    @DisplayName("Should validate search request with invalid date range")
    void searchTransactionsWithInvalidDateRange() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().minusDays(1)) // End before start
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", notNullValue()));
    }

    @Test
    @DisplayName("Should validate search request with invalid amount range")
    void searchTransactionsWithInvalidAmountRange() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .minAmount(new BigDecimal("1000.00"))
                .maxAmount(new BigDecimal("100.00")) // Max less than min
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors", notNullValue()));
    }

    @Test
    @DisplayName("Should return empty results for no matching transactions")
    void searchTransactionsNoResults() throws Exception {
        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .descriptionKeyword("nonexistent")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    @DisplayName("Should forbid searching another user's transactions")
    void searchTransactionsUnauthorized() throws Exception {
        // Create another user and login
        CreateUserRequest otherUser = CreateUserRequest.builder()
                .email("other@example.com")
                .password("Password123!")
                .firstName("Other")
                .lastName("User")
                .phoneNumber("+1234567891")
                .address("456 Other Ave")
                .build();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherUser)))
                .andExpect(status().isCreated());

        LoginRequest otherLogin = LoginRequest.builder()
                .email("other@example.com")
                .password("Password123!")
                .build();

        MvcResult otherLoginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse otherAuth = objectMapper.readValue(
                otherLoginResult.getResponse().getContentAsString(), 
                AuthResponse.class);

        TransactionSearchRequest searchRequest = TransactionSearchRequest.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .build();

        // Try to search first user's transactions with second user's token
        mockMvc.perform(post("/v1/accounts/{accountId}/transactions/search", accountId)
                        .header("Authorization", "Bearer " + otherAuth.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isForbidden());
    }
}