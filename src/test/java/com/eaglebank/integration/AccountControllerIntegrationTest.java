package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account.AccountType;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;
    private String userId;
    private String otherUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create first user and get JWT token
        CreateUserRequest user1 = CreateUserRequest.builder()
                .email("user1@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("One")
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
        userId = userResponse.getId().toString();

        // Login to get JWT token
        LoginRequest loginRequest = LoginRequest.builder()
                .email("user1@example.com")
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

        // Create second user for authorization testing
        CreateUserRequest user2 = CreateUserRequest.builder()
                .email("user2@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("Two")
                .phoneNumber("+1234567891")
                .address("456 Oak St")
                .build();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated());

        // Login as second user
        LoginRequest loginRequest2 = LoginRequest.builder()
                .email("user2@example.com")
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
    }

    @Test
    void createAccount_Success() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber", notNullValue()))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", is(1000.00)))
                .andExpect(jsonPath("$.userId", is(userId)));
    }

    @Test
    void createAccount_WithoutAuth_Returns401() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_InvalidRequest_Returns400() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(null) // Missing required field
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void createAccount_NegativeBalance_Returns400() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .initialBalance(new BigDecimal("-100.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.initialBalance", notNullValue()));
    }

    @Test
    void getUserAccounts_Success() throws Exception {
        // Create multiple accounts
        CreateAccountRequest checkingRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        CreateAccountRequest savingsRequest = CreateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .initialBalance(new BigDecimal("5000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkingRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(savingsRequest)))
                .andExpect(status().isCreated());

        // Get all accounts
        mockMvc.perform(get("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].accountType", isOneOf("CHECKING", "SAVINGS")))
                .andExpect(jsonPath("$.content[1].accountType", isOneOf("CHECKING", "SAVINGS")))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void getUserAccounts_EmptyList() throws Exception {
        mockMvc.perform(get("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void getAccountById_Success() throws Exception {
        // Create an account
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1500.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Get the account by ID
        mockMvc.perform(get("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(account.getId().toString())))
                .andExpect(jsonPath("$.accountNumber", is(account.getAccountNumber())))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", is(1500.00)));
    }

    @Test
    void getAccountById_NotOwner_Returns403() throws Exception {
        // Create an account as user1
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1500.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Try to access as user2
        mockMvc.perform(get("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not authorized")));
    }

    @Test
    void getAccountById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/v1/accounts/{accountId}", "01987617-0816-7be0-b07d-a64681b91553")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    void updateAccount_Success() throws Exception {
        // Create an account
        CreateAccountRequest createRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("2000.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Update the account type
        String updateJson = "{\"accountType\":\"SAVINGS\"}";

        mockMvc.perform(patch("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(account.getId().toString())))
                .andExpect(jsonPath("$.accountType", is("SAVINGS")))
                .andExpect(jsonPath("$.balance", is(2000.00)));
    }

    @Test
    void updateAccount_NotOwner_Returns403() throws Exception {
        // Create an account as user1
        CreateAccountRequest createRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("2000.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Try to update as user2
        String updateJson = "{\"accountType\":\"SAVINGS\"}";

        mockMvc.perform(patch("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAccount_Success() throws Exception {
        // Create an account with zero balance
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(BigDecimal.ZERO)
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Delete the account (should work with zero balance)
        mockMvc.perform(delete("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccount_WithBalance_Returns409() throws Exception {
        // Create an account with balance
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Try to delete the account with balance
        mockMvc.perform(delete("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("zero balance")));
    }

    @Test
    void deleteAccount_NotOwner_Returns403() throws Exception {
        // Create an account as user1
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(BigDecimal.ZERO)
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Try to delete as user2
        mockMvc.perform(delete("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeAccountFlow() throws Exception {
        // Create account
        CreateAccountRequest createRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), 
                AccountResponse.class);

        // Get account
        mockMvc.perform(get("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(1000.00)));

        // Update account
        String updateJson = "{\"accountType\":\"SAVINGS\"}";
        mockMvc.perform(patch("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType", is("SAVINGS")));

        // List accounts
        mockMvc.perform(get("/v1/accounts")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(account.getId().toString())));

        // Try to delete account with balance - should fail
        mockMvc.perform(delete("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isConflict());

        // Verify account still exists
        mockMvc.perform(get("/v1/accounts/{accountId}", account.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }
}