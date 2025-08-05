package com.eaglebank.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to check password strength")
public class PasswordStrengthRequest {
    
    @NotBlank(message = "Password is required")
    @Schema(description = "Password to check", example = "MyStr0ng!P@ssw0rd")
    private String password;
    
    @Schema(description = "User's email (used to prevent using email in password)", example = "john.doe@eaglebank.com")
    private String email;
    
    @Schema(description = "User's first name (used to prevent using name in password)", example = "John")
    private String firstName;
    
    @Schema(description = "User's last name (used to prevent using name in password)", example = "Doe")
    private String lastName;
}