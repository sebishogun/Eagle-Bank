package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.security.CustomUserDetailsService;
import com.eaglebank.security.JwtAuthenticationEntryPoint;
import com.eaglebank.security.JwtAuthenticationFilter;
import com.eaglebank.security.JwtTokenProvider;
import com.eaglebank.service.AccountService;
import com.eaglebank.util.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private AccountResponse testAccountResponse;
    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        accountId = UuidGenerator.generateUuidV7();
        
        testAccountResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("ACC1234567890")
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser
    void createAccount_Success() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        when(accountService.createAccount(any(UUID.class), any(CreateAccountRequest.class)))
                .thenReturn(testAccountResponse);

        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.accountNumber", is("ACC1234567890")))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", is(1000.00)));

        verify(accountService, times(1)).createAccount(any(UUID.class), any(CreateAccountRequest.class));
    }

    @Test
    @WithMockUser
    void createAccount_InvalidRequest_ReturnsBadRequest() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(null)  // Missing required field
                .initialBalance(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(UUID.class), any(CreateAccountRequest.class));
    }

    @Test
    @WithMockUser
    void createAccount_NegativeBalance_ReturnsBadRequest() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(AccountType.CHECKING)
                .initialBalance(new BigDecimal("-100.00"))
                .build();

        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).createAccount(any(UUID.class), any(CreateAccountRequest.class));
    }

    @Test
    @WithMockUser
    void getAccountById_Success() throws Exception {
        when(accountService.getAccountById(any(UUID.class), eq(accountId)))
                .thenReturn(testAccountResponse);

        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId.toString())))
                .andExpect(jsonPath("$.accountNumber", is("ACC1234567890")));

        verify(accountService, times(1)).getAccountById(any(UUID.class), eq(accountId));
    }

    @Test
    @WithMockUser
    void getAccountById_NotFound_Returns404() throws Exception {
        when(accountService.getAccountById(any(UUID.class), eq(accountId)))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(accountService, times(1)).getAccountById(any(UUID.class), eq(accountId));
    }

    @Test
    @WithMockUser
    void getAccountById_Unauthorized_Returns403() throws Exception {
        when(accountService.getAccountById(any(UUID.class), eq(accountId)))
                .thenThrow(new UnauthorizedException("Not authorized"));

        mockMvc.perform(get("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(accountService, times(1)).getAccountById(any(UUID.class), eq(accountId));
    }

    @Test
    @WithMockUser
    void getUserAccounts_Success() throws Exception {
        Page<AccountResponse> accountPage = new PageImpl<>(
                List.of(testAccountResponse),
                PageRequest.of(0, 10),
                1
        );

        when(accountService.getUserAccounts(any(UUID.class), any()))
                .thenReturn(accountPage);

        mockMvc.perform(get("/v1/accounts")
                        .with(csrf())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(accountId.toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));

        verify(accountService, times(1)).getUserAccounts(any(UUID.class), any());
    }

    @Test
    @WithMockUser
    void updateAccount_Success() throws Exception {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .build();

        AccountResponse updatedResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("ACC1234567890")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(accountService.updateAccount(any(UUID.class), eq(accountId), any(UpdateAccountRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(patch("/v1/accounts/{accountId}", accountId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType", is("SAVINGS")));

        verify(accountService, times(1)).updateAccount(any(UUID.class), eq(accountId), any(UpdateAccountRequest.class));
    }

    @Test
    @WithMockUser
    void updateAccount_NotFound_Returns404() throws Exception {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .accountType(AccountType.SAVINGS)
                .build();

        when(accountService.updateAccount(any(UUID.class), eq(accountId), any(UpdateAccountRequest.class)))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        mockMvc.perform(patch("/v1/accounts/{accountId}", accountId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(accountService, times(1)).updateAccount(any(UUID.class), eq(accountId), any(UpdateAccountRequest.class));
    }

    @Test
    @WithMockUser
    void deleteAccount_Success() throws Exception {
        doNothing().when(accountService).deleteAccount(any(UUID.class), eq(accountId));

        mockMvc.perform(delete("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(accountService, times(1)).deleteAccount(any(UUID.class), eq(accountId));
    }

    @Test
    @WithMockUser
    void deleteAccount_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Account not found"))
                .when(accountService).deleteAccount(any(UUID.class), eq(accountId));

        mockMvc.perform(delete("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(accountService, times(1)).deleteAccount(any(UUID.class), eq(accountId));
    }

    @Test
    @WithMockUser
    void deleteAccount_HasTransactions_ReturnsConflict() throws Exception {
        doThrow(new IllegalStateException("Cannot delete account with transaction history"))
                .when(accountService).deleteAccount(any(UUID.class), eq(accountId));

        mockMvc.perform(delete("/v1/accounts/{accountId}", accountId)
                        .with(csrf()))
                .andExpect(status().isConflict());

        verify(accountService, times(1)).deleteAccount(any(UUID.class), eq(accountId));
    }

    @Test
    void allEndpoints_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/accounts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/accounts/{accountId}", accountId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/v1/accounts/{accountId}", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/v1/accounts/{accountId}", accountId))
                .andExpect(status().isUnauthorized());
    }
}