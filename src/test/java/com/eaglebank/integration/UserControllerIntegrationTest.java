package com.eaglebank.integration;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.request.UpdateUserRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.Account.AccountType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser_Success() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .email("newuser@example.com")
                .password("Password123!")
                .firstName("New")
                .lastName("User")
                .phoneNumber("+1234567890")
                .address("123 Test St")
                .build();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("newuser@example.com")))
                .andExpect(jsonPath("$.firstName", is("New")))
                .andExpect(jsonPath("$.lastName", is("User")));
    }

    @Test
    void updateUser_Success() throws Exception {
        // Create user
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .email("update_test@example.com")
                .password("Password123!")
                .firstName("Original")
                .lastName("Name")
                .phoneNumber("+1234567890")
                .address("123 Old St")
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                UserResponse.class);

        // Login to get token
        LoginRequest loginRequest = LoginRequest.builder()
                .email("update_test@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        // Update user
        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .firstName("Updated")
                .lastName("NewName")
                .address("456 New St")
                .build();

        mockMvc.perform(patch("/v1/users/{userId}", user.getId())
                        .header("Authorization", "Bearer " + auth.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Updated")))
                .andExpect(jsonPath("$.lastName", is("NewName")))
                .andExpect(jsonPath("$.address", is("456 New St")))
                .andExpect(jsonPath("$.phoneNumber", is("+1234567890"))); // Unchanged
    }

    @Test
    void updateUser_OtherUser_Returns403() throws Exception {
        // Create two users
        CreateUserRequest user1Request = CreateUserRequest.builder()
                .email("user1_update@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("One")
                .phoneNumber("+1234567890")
                .address("123 St")
                .build();

        CreateUserRequest user2Request = CreateUserRequest.builder()
                .email("user2_update@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("Two")
                .phoneNumber("+1234567891")
                .address("456 St")
                .build();

        MvcResult user1Result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user1 = objectMapper.readValue(
                user1Result.getResponse().getContentAsString(),
                UserResponse.class);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2Request)))
                .andExpect(status().isCreated());

        // Login as user2
        LoginRequest loginRequest = LoginRequest.builder()
                .email("user2_update@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        // Try to update user1 as user2
        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .firstName("Hacked")
                .build();

        mockMvc.perform(patch("/v1/users/{userId}", user1.getId())
                        .header("Authorization", "Bearer " + auth.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("can only update")));
    }

    @Test
    void deleteUser_Success() throws Exception {
        // Create user
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .email("delete_test@example.com")
                .password("Password123!")
                .firstName("Delete")
                .lastName("Me")
                .phoneNumber("+1234567890")
                .address("123 St")
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                UserResponse.class);

        // Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("delete_test@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        // Delete user
        mockMvc.perform(delete("/v1/users/{userId}", user.getId())
                        .header("Authorization", "Bearer " + auth.getToken()))
                .andExpect(status().isNoContent());

        // Verify user is deleted (login should fail)
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_WithAccounts_Returns409() throws Exception {
        // Create user
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .email("delete_with_accounts@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("WithAccounts")
                .phoneNumber("+1234567890")
                .address("123 St")
                .build();

        MvcResult createResult = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                UserResponse.class);

        // Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email("delete_with_accounts@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        // Create an account
        CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + auth.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accountRequest)))
                .andExpect(status().isCreated());

        // Try to delete user with account
        mockMvc.perform(delete("/v1/users/{userId}", user.getId())
                        .header("Authorization", "Bearer " + auth.getToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cannot delete user")))
                .andExpect(jsonPath("$.message", containsString("existing account")));
    }

    @Test
    void deleteUser_OtherUser_Returns403() throws Exception {
        // Create two users
        CreateUserRequest user1Request = CreateUserRequest.builder()
                .email("user1_delete@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("One")
                .phoneNumber("+1234567890")
                .address("123 St")
                .build();

        CreateUserRequest user2Request = CreateUserRequest.builder()
                .email("user2_delete@example.com")
                .password("Password123!")
                .firstName("User")
                .lastName("Two")
                .phoneNumber("+1234567891")
                .address("456 St")
                .build();

        MvcResult user1Result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        UserResponse user1 = objectMapper.readValue(
                user1Result.getResponse().getContentAsString(),
                UserResponse.class);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2Request)))
                .andExpect(status().isCreated());

        // Login as user2
        LoginRequest loginRequest = LoginRequest.builder()
                .email("user2_delete@example.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        // Try to delete user1 as user2
        mockMvc.perform(delete("/v1/users/{userId}", user1.getId())
                        .header("Authorization", "Bearer " + auth.getToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("can only delete")));
    }

    @Test
    void updateUser_WithoutAuth_Returns401() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Updated")
                .build();

        mockMvc.perform(patch("/v1/users/{userId}", "01987617-0816-7be0-b07d-a64681b91553")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(delete("/v1/users/{userId}", "01987617-0816-7be0-b07d-a64681b91553"))
                .andExpect(status().isUnauthorized());
    }
}