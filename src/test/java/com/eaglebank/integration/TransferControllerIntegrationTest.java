package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateTransferRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.dto.response.TransferResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private UUID user1AccountId;
    private UUID user2AccountId;
    private UUID user1Id;
    private UUID user2Id;

    @BeforeEach
    void setUp() throws Exception {
        // Use timestamp to ensure unique emails across test runs
        long timestamp = System.currentTimeMillis();
        
        // Create first user
        CreateUserRequest user1 = CreateUserRequest.builder()
                .email("transfer_user1_" + timestamp + "@example.com")
                .password("Password123!")
                .firstName("Transfer")
                .lastName("User1")
                .phoneNumber("+1234567890")
                .address("123 Main St")
                .build();

        MvcResult user1Result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user1Response = objectMapper.readValue(
                user1Result.getResponse().getContentAsString(), 
                UserResponse.class);
        user1Id = user1Response.getId();

        // Create second user
        CreateUserRequest user2 = CreateUserRequest.builder()
                .email("transfer_user2_" + timestamp + "@example.com")
                .password("Password456!")
                .firstName("Transfer")
                .lastName("User2")
                .phoneNumber("+9876543210")
                .address("456 Oak Ave")
                .build();

        MvcResult user2Result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user2Response = objectMapper.readValue(
                user2Result.getResponse().getContentAsString(), 
                UserResponse.class);
        user2Id = user2Response.getId();

        // Login as first user
        LoginRequest loginRequest1 = LoginRequest.builder()
                .email("transfer_user1_" + timestamp + "@example.com")
                .password("Password123!")
                .build();

        MvcResult login1Result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest1)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth1Response = objectMapper.readValue(
                login1Result.getResponse().getContentAsString(), 
                AuthResponse.class);
        user1Token = auth1Response.getToken();

        // Login as second user
        LoginRequest loginRequest2 = LoginRequest.builder()
                .email("transfer_user2_" + timestamp + "@example.com")
                .password("Password456!")
                .build();

        MvcResult login2Result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest2)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth2Response = objectMapper.readValue(
                login2Result.getResponse().getContentAsString(), 
                AuthResponse.class);
        user2Token = auth2Response.getToken();

        // Create account for first user with initial balance
        CreateAccountRequest account1Request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .accountName("User1 Checking")
                .initialBalance(new BigDecimal("5000.00"))
                .build();

        MvcResult account1Result = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(account1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account1Response = objectMapper.readValue(
                account1Result.getResponse().getContentAsString(), 
                AccountResponse.class);
        user1AccountId = account1Response.getId();

        // Create account for second user with initial balance
        CreateAccountRequest account2Request = CreateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .accountName("User2 Savings")
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        MvcResult account2Result = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(account2Request)))
                .andExpect(status().isCreated())
                .andReturn();

        AccountResponse account2Response = objectMapper.readValue(
                account2Result.getResponse().getContentAsString(), 
                AccountResponse.class);
        user2AccountId = account2Response.getId();
    }

    @Test
    @DisplayName("Should successfully transfer money between accounts")
    void shouldSuccessfullyTransferBetweenAccounts() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("500.00"))
                .description("Test transfer")
                .build();

        MvcResult result = mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferReference").exists())
                .andExpect(jsonPath("$.sourceAccountId").value(user1AccountId.toString()))
                .andExpect(jsonPath("$.targetAccountId").value(user2AccountId.toString()))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceTransaction").exists())
                .andExpect(jsonPath("$.targetTransaction").exists())
                .andReturn();

        TransferResponse transferResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransferResponse.class);

        // Verify source transaction
        assertNotNull(transferResponse.getSourceTransaction());
        assertEquals(new BigDecimal("500.00"), transferResponse.getSourceTransaction().getAmount());
        assertEquals(new BigDecimal("4500.00"), transferResponse.getSourceTransaction().getBalanceAfter());

        // Verify target transaction
        assertNotNull(transferResponse.getTargetTransaction());
        assertEquals(new BigDecimal("500.00"), transferResponse.getTargetTransaction().getAmount());
        assertEquals(new BigDecimal("1500.00"), transferResponse.getTargetTransaction().getBalanceAfter());

        // Verify account balances are updated
        mockMvc.perform(get("/v1/accounts/" + user1AccountId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(4500.00));

        mockMvc.perform(get("/v1/accounts/" + user2AccountId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500.00));
    }

    @Test
    @DisplayName("Should reject transfer from account user doesn't own")
    void shouldRejectTransferFromUnownedAccount() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user2AccountId)  // User2's account
                .targetAccountId(user1AccountId)
                .amount(new BigDecimal("100.00"))
                .description("Unauthorized transfer")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)  // User1 trying to transfer from User2's account
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User is not authorized to transfer from this account"));
    }

    @Test
    @DisplayName("Should allow transfer to account user doesn't own")
    void shouldAllowTransferToUnownedAccount() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)  // User2's account
                .amount(new BigDecimal("250.00"))
                .description("Payment to another user")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Should reject transfer with insufficient funds")
    void shouldRejectTransferWithInsufficientFunds() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("10000.00"))  // More than available balance
                .description("Large transfer")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient funds")));
    }

    @Test
    @DisplayName("Should reject transfer to same account")
    void shouldRejectTransferToSameAccount() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user1AccountId)  // Same account
                .amount(new BigDecimal("100.00"))
                .description("Self transfer")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and target accounts must be different"));
    }

    @Test
    @DisplayName("Should reject transfer with negative amount")
    void shouldRejectTransferWithNegativeAmount() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("-100.00"))
                .description("Negative transfer")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").value("Amount must be positive"));
    }

    @Test
    @DisplayName("Should reject transfer with zero amount")
    void shouldRejectTransferWithZeroAmount() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(BigDecimal.ZERO)
                .description("Zero transfer")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").value("Amount must be positive"));
    }

    @Test
    @DisplayName("Should reject transfer from frozen account")
    void shouldRejectTransferFromFrozenAccount() throws Exception {
        // Freeze the source account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Security review")
                .build();

        mockMvc.perform(patch("/v1/accounts/" + user1AccountId)
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freezeRequest)))
                .andExpect(status().isOk());

        // Attempt transfer from frozen account
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("100.00"))
                .description("Transfer from frozen")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Source account cannot transfer")));
    }

    @Test
    @DisplayName("Should allow transfer to frozen account")
    void shouldAllowTransferToFrozenAccount() throws Exception {
        // Freeze the target account
        UpdateAccountRequest freezeRequest = UpdateAccountRequest.builder()
                .status(Account.AccountStatus.FROZEN)
                .statusChangeReason("Security review")
                .build();

        mockMvc.perform(patch("/v1/accounts/" + user2AccountId)
                        .header("Authorization", "Bearer " + user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freezeRequest)))
                .andExpect(status().isOk());

        // Transfer to frozen account should succeed (for debt recovery)
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("100.00"))
                .description("Payment to frozen account")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Should handle large amount transfers with precision")
    void shouldHandleLargeAmountTransfers() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("4999.99"))  // Nearly all balance
                .description("Large transfer")
                .build();

        MvcResult result = mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransferResponse.class);

        // Verify precision is maintained
        assertEquals(new BigDecimal("4999.99"), response.getAmount());
        assertEquals(new BigDecimal("0.01"), response.getSourceTransaction().getBalanceAfter());
        assertEquals(new BigDecimal("5999.99"), response.getTargetTransaction().getBalanceAfter());
    }

    @Test
    @DisplayName("Should return 401 for unauthorized transfer request")
    void shouldReturn401ForUnauthorizedTransfer() throws Exception {
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("100.00"))
                .description("Unauthorized")
                .build();

        mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should generate unique transfer references")
    void shouldGenerateUniqueTransferReferences() throws Exception {
        CreateTransferRequest transferRequest1 = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("50.00"))
                .description("Transfer 1")
                .build();

        CreateTransferRequest transferRequest2 = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("75.00"))
                .description("Transfer 2")
                .build();

        MvcResult result1 = mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest1)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest2)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(),
                TransferResponse.class);
        TransferResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(),
                TransferResponse.class);

        assertNotEquals(response1.getTransferReference(), response2.getTransferReference());
        assertTrue(response1.getTransferReference().startsWith("TRF"));
        assertTrue(response2.getTransferReference().startsWith("TRF"));
    }

    @Test
    @DisplayName("Should include transfer description in transactions")
    void shouldIncludeTransferDescription() throws Exception {
        String customDescription = "Monthly rent payment";
        CreateTransferRequest transferRequest = CreateTransferRequest.builder()
                .sourceAccountId(user1AccountId)
                .targetAccountId(user2AccountId)
                .amount(new BigDecimal("1200.00"))
                .description(customDescription)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/transfers")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransferResponse.class);

        assertTrue(response.getSourceTransaction().getDescription().contains(customDescription));
        assertTrue(response.getTargetTransaction().getDescription().contains(customDescription));
    }
}