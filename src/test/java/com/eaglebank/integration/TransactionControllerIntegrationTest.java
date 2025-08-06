package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.entity.Transaction.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.eaglebank.config.TestStrategyConfiguration;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;
    private String otherUserToken;
    private UUID accountId;
    private UUID otherUserAccountId;

    @BeforeEach
    void setUp() throws Exception {
        // Create first user
        CreateUserRequest user1 = CreateUserRequest.builder()
                .email("trans_user1@example.com")
                .password("Password123!")
                .firstName("Trans")
                .lastName("User1")
                .phoneNumber("+1234567890")
                .address("123 Main St")
                .build();

        MvcResult userResult = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse userResponse = objectMapper.readValue(
                userResult.getResponse().getContentAsString(), 
                UserResponse.class);

        // Login as first user
        LoginRequest loginRequest = LoginRequest.builder()
                .email("trans_user1@example.com")
                .password("Password123!")
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

        // Create account for first user
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("5000.00"))
                .build();

        MvcResult accountResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse accountResponse = objectMapper.readValue(
                accountResult.getResponse().getContentAsString(), 
                AccountResponse.class);
        accountId = accountResponse.getId();

        // Create second user for authorization testing
        CreateUserRequest user2 = CreateUserRequest.builder()
                .email("trans_user2@example.com")
                .password("Password123!")
                .firstName("Trans")
                .lastName("User2")
                .phoneNumber("+1234567891")
                .address("456 Oak St")
                .build();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated());

        // Login as second user
        LoginRequest loginRequest2 = LoginRequest.builder()
                .email("trans_user2@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult2 = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest2)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse2 = objectMapper.readValue(
                loginResult2.getResponse().getContentAsString(), 
                AuthResponse.class);
        otherUserToken = authResponse2.getToken();

        // Create account for second user
        CreateAccountRequest accountRequest2 = CreateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .initialBalance(new BigDecimal("3000.00"))
                .build();

        MvcResult accountResult2 = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest2)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse accountResponse2 = objectMapper.readValue(
                accountResult2.getResponse().getContentAsString(), 
                AccountResponse.class);
        otherUserAccountId = accountResponse2.getId();
    }

    @Test
    void createDeposit_Success() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Salary deposit")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference", notNullValue()))
                .andExpect(jsonPath("$.transactionType", is("DEPOSIT")))
                .andExpect(jsonPath("$.amount", is(500.00)))
                .andExpect(jsonPath("$.balanceBefore", is(5000.00)))
                .andExpect(jsonPath("$.balanceAfter", is(5500.00)))
                .andExpect(jsonPath("$.description", is("Salary deposit")));

        // Verify account balance was updated
        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(5500.00)));
    }

    @Test
    void createWithdrawal_Success() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000.00"))
                .description("ATM withdrawal")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference", notNullValue()))
                .andExpect(jsonPath("$.transactionType", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.amount", is(1000.00)))
                .andExpect(jsonPath("$.balanceBefore", is(5000.00)))
                .andExpect(jsonPath("$.balanceAfter", is(4000.00)))
                .andExpect(jsonPath("$.description", is("ATM withdrawal")));

        // Verify account balance was updated
        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(4000.00)));
    }

    @Test
    void createWithdrawal_InsufficientFunds_Returns422() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("10000.00")) // More than balance
                .description("Large withdrawal")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")))
                .andExpect(jsonPath("$.message", containsString("5000"))) // Available balance
                .andExpect(jsonPath("$.message", containsString("10000"))); // Requested amount

        // Verify account balance was NOT updated
        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(5000.00)));
    }

    @Test
    void createTransaction_NotAccountOwner_Returns403() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Unauthorized deposit attempt")
                .build();

        // Try to create transaction on user1's account as user2
        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not authorized")));
    }

    @Test
    void createTransaction_InvalidAmount_Returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("-100.00")) // Negative amount
                .description("Invalid deposit")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount", notNullValue()));
    }

    @Test
    void createTransaction_ZeroAmount_Returns400() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(BigDecimal.ZERO)
                .description("Zero deposit")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount", notNullValue()));
    }

    @Test
    void createTransaction_WithoutAuth_Returns401() throws Exception {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .description("Unauthorized deposit")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccountTransactions_Success() throws Exception {
        // Create multiple transactions
        CreateTransactionRequest deposit1 = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .description("Deposit 1")
                .build();

        CreateTransactionRequest withdrawal = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("50.00"))
                .description("Withdrawal")
                .build();

        CreateTransactionRequest deposit2 = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("200.00"))
                .description("Deposit 2")
                .build();

        // Create transactions with small delays to ensure different timestamps
        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit1)))
                .andExpect(status().isCreated());
        
        Thread.sleep(50); // Delay to ensure different timestamp

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawal)))
                .andExpect(status().isCreated());
        
        Thread.sleep(50); // Delay to ensure different timestamp

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit2)))
                .andExpect(status().isCreated());

        // Get all transactions (should be in descending order by date)
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].description", is("Deposit 2"))) // Most recent (descending order)
                .andExpect(jsonPath("$.content[1].description", is("Withdrawal")))
                .andExpect(jsonPath("$.content[2].description", is("Deposit 1"))) // Oldest
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    void getAccountTransactions_EmptyList() throws Exception {
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void getAccountTransactions_NotOwner_Returns403() throws Exception {
        // Try to get transactions from user1's account as user2
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not authorized")));
    }

    @Test
    void getTransactionById_Success() throws Exception {
        // Create a transaction
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("750.00"))
                .description("Test deposit")
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TransactionResponse transaction = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                TransactionResponse.class);

        // Get the transaction by ID
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions/{transactionId}", 
                        accountId, transaction.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(transaction.getId().toString())))
                .andExpect(jsonPath("$.transactionReference", is(transaction.getTransactionReference())))
                .andExpect(jsonPath("$.transactionType", is("DEPOSIT")))
                .andExpect(jsonPath("$.amount", is(750.00)))
                .andExpect(jsonPath("$.description", is("Test deposit")));
    }

    @Test
    void getTransactionById_NotOwner_Returns403() throws Exception {
        // Create a transaction as user1
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("750.00"))
                .description("Test deposit")
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TransactionResponse transaction = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                TransactionResponse.class);

        // Try to get the transaction as user2
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions/{transactionId}", 
                        accountId, transaction.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions/{transactionId}", 
                        accountId, "01987617-0816-7be0-b07d-a64681b91553")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    void completeTransactionFlow() throws Exception {
        // Initial balance: 5000

        // Deposit 1000
        CreateTransactionRequest deposit = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("1000.00"))
                .description("Salary")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter", is(6000.00)));

        // Withdraw 500
        CreateTransactionRequest withdrawal1 = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("500.00"))
                .description("Bills")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawal1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter", is(5500.00)));

        // Deposit 2000
        CreateTransactionRequest deposit2 = CreateTransactionRequest.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("2000.00"))
                .description("Bonus")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter", is(7500.00)));

        // Withdraw 1500
        CreateTransactionRequest withdrawal2 = CreateTransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1500.00"))
                .description("Shopping")
                .build();

        mockMvc.perform(post("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawal2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter", is(6000.00)));

        // Verify final balance
        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(6000.00)));

        // Get transaction history
        mockMvc.perform(get("/v1/accounts/{accountId}/transactions", accountId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.totalElements", is(4)));
    }
}