package com.eaglebank.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to change user password")
public class ChangePasswordRequest {
    
    @NotBlank(message = "Current password is required")
    @Schema(description = "Current password", example = "CurrentP@ssw0rd!")
    private String currentPassword;
    
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters long")
    @Schema(description = "New password", example = "NewStr0ng!P@ssw0rd")
    private String newPassword;
    
    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "New password confirmation", example = "NewStr0ng!P@ssw0rd")
    private String confirmPassword;
}