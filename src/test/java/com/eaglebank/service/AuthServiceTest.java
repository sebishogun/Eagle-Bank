package com.eaglebank.service;

import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.entity.User;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.security.JwtTokenProvider;
import com.eaglebank.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    
    @Mock
    private AuthenticationManager authenticationManager;
    
    @Mock
    private JwtTokenProvider tokenProvider;
    
    @Mock
    private Authentication authentication;
    
    @InjectMocks
    private AuthService authService;
    
    private LoginRequest loginRequest;
    private User user;
    private UserPrincipal userPrincipal;
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        loginRequest = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("SecurePass123!")
                .build();
        
        user = User.builder()
                .id(userId)
                .email("john.doe@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .build();
        
        userPrincipal = UserPrincipal.create(user);
    }
    
    @Test
    void login_ShouldReturnAuthResponse_WhenCredentialsAreValid() {
        // Given
        String token = "jwt.token.here";
        Date expirationDate = new Date(System.currentTimeMillis() + 86400000); // 24 hours from now
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(tokenProvider.generateToken(userId, "john.doe@example.com")).thenReturn(token);
        when(tokenProvider.getExpirationDateFromToken(anyString())).thenReturn(expirationDate);
        
        // When
        AuthResponse response = authService.login(loginRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo(token);
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider).generateToken(userId, "john.doe@example.com");
    }
    
    @Test
    void login_ShouldThrowUnauthorizedException_WhenCredentialsAreInvalid() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));
        
        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenProvider, never()).generateToken(any(), any());
    }
}