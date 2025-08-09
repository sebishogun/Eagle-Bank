package com.eaglebank.controller;

import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.security.JwtAuthenticationEntryPoint;
import com.eaglebank.security.JwtAuthenticationFilter;
import com.eaglebank.security.JwtTokenProvider;
import com.eaglebank.security.RateLimitingFilter;
import com.eaglebank.security.SecurityHeadersFilter;
import com.eaglebank.service.AuthService;
import com.eaglebank.service.JweTokenService;
import com.eaglebank.service.KeyManagementService;
import com.eaglebank.service.RefreshTokenService;
import com.eaglebank.service.TokenBlacklistService;
import com.eaglebank.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockitoBean
    private AuthService authService;
    
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    @MockitoBean
    private UserDetailsService userDetailsService;
    
    @MockitoBean
    private SecurityHeadersFilter securityHeadersFilter;
    
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    
    @MockitoBean
    private UserService userService;
    
    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;
    
    @MockitoBean
    private JweTokenService jweTokenService;
    
    @MockitoBean
    private KeyManagementService keyManagementService;
    
    private LoginRequest loginRequest;
    private AuthResponse authResponse;
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        loginRequest = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("SecurePass123!")
                .build();
        
        authResponse = AuthResponse.builder()
                .token("jwt.token.here")
                .type("Bearer")
                .userId(userId)
                .email("john.doe@example.com")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
    
    @Test
    void login_ShouldReturnAuthResponse_WhenCredentialsAreValid() throws Exception {
        // Given
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);
        
        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }
    
    @Test
    void login_ShouldReturnUnauthorized_WhenCredentialsAreInvalid() throws Exception {
        // Given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid email or password"));
        
        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void login_ShouldReturnBadRequest_WhenEmailIsInvalid() throws Exception {
        // Given
        loginRequest.setEmail("invalid-email");
        
        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void login_ShouldReturnBadRequest_WhenPasswordIsMissing() throws Exception {
        // Given
        loginRequest.setPassword(null);
        
        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }
}