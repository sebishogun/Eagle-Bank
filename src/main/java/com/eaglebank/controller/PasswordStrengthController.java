package com.eaglebank.controller;

import com.eaglebank.dto.request.PasswordStrengthRequest;
import com.eaglebank.dto.response.PasswordStrengthResponse;
import com.eaglebank.service.PasswordStrengthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth/password-strength")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Password Strength", description = "Password strength validation endpoints")
public class PasswordStrengthController {
    
    private final PasswordStrengthService passwordStrengthService;
    
    @PostMapping
    @Operation(
        summary = "Check password strength", 
        description = "Analyzes password strength using entropy-based validation and provides feedback"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password strength analysis completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<PasswordStrengthResponse> checkPasswordStrength(
            @Valid @RequestBody PasswordStrengthRequest request) {
        
        log.debug("Checking password strength for user context");
        
        PasswordStrengthResponse response = passwordStrengthService.checkPasswordStrength(
                request.getPassword(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );
        
        return ResponseEntity.ok(response);
    }
}