package com.eaglebank.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Password strength analysis response")
public class PasswordStrengthResponse {
    
    @Schema(description = "Whether the password meets minimum security requirements", example = "true")
    private boolean isAcceptable;
    
    @Schema(description = "Password entropy score", example = "42.5")
    private double entropy;
    
    @Schema(description = "Simple score from 0-4 (0=very weak, 4=very strong)", example = "3")
    private int score;
    
    @Schema(description = "Human-readable estimated crack time", example = "2 years")
    private String estimatedCrackTime;
    
    @Schema(description = "List of suggestions to improve password strength")
    private List<String> suggestions;
    
    @Schema(description = "Warning message if password contains personal information", example = "Password contains your email address")
    private String warning;
}