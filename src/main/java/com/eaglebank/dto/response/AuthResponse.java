package com.eaglebank.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response with tokens")
public class AuthResponse {
    
    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
    
    @Schema(description = "Token type", example = "Bearer")
    @Builder.Default
    private String type = "Bearer";
    
    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;
    
    @Schema(description = "User email", example = "john.doe@eaglebank.com")
    private String email;
    
    @Schema(description = "Token expiration time")
    private LocalDateTime expiresAt;
    
    @Schema(description = "Refresh token for getting new access tokens", example = "550e8400-e29b-41d4-a716-446655440001")
    private String refreshToken;
}