package com.eaglebank.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user information")
public class UpdateUserRequest {
    
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    @Schema(description = "User's first name", example = "John")
    private String firstName;
    
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    @Schema(description = "User's last name", example = "Doe")
    private String lastName;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "User's phone number in E.164 format", example = "+1234567890")
    private String phoneNumber;
    
    @Size(min = 5, max = 200, message = "Address must be between 5 and 200 characters")
    @Schema(description = "User's address", example = "123 Main St, City, State 12345")
    private String address;
    
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
    @Schema(description = "User's new password", example = "NewPassword123!")
    private String password;
}