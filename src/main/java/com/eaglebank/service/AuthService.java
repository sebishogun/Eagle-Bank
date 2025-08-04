package com.eaglebank.service;

import com.eaglebank.dto.request.LoginRequest;
import com.eaglebank.dto.response.AuthResponse;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.security.JwtTokenProvider;
import com.eaglebank.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    
    public AuthResponse login(LoginRequest loginRequest) {
        log.debug("Attempting to authenticate user: {}", loginRequest.getEmail());
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String jwt = tokenProvider.generateToken(userPrincipal.getId(), userPrincipal.getEmail());
            
            Date expirationDate = tokenProvider.getExpirationDateFromToken(jwt);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                    expirationDate.toInstant(), 
                    ZoneId.systemDefault()
            );
            
            log.info("User {} authenticated successfully", loginRequest.getEmail());
            
            return AuthResponse.builder()
                    .token(jwt)
                    .type("Bearer")
                    .userId(userPrincipal.getId())
                    .email(userPrincipal.getEmail())
                    .expiresAt(expiresAt)
                    .build();
            
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user: {}", loginRequest.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        } catch (AuthenticationException e) {
            log.error("Authentication error for user: {}", loginRequest.getEmail(), e);
            throw new UnauthorizedException("Authentication failed", e);
        }
    }
}