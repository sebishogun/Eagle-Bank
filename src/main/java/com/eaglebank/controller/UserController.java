package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.UpdateUserRequest;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
public class UserController {
    
    private final UserService userService;
    
    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "User with email already exists")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());
        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Fetches user details by user ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - accessing another user's data")
    })
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        log.info("Fetching user with id: {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{userId}")
    @Operation(summary = "Update user", description = "Updates user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - can only update own account"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user {} by requester {}", userId, userPrincipal.getId());
        UserResponse response = userService.updateUser(userId, userPrincipal.getId(), request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Deletes a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - can only delete own account"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - user has existing accounts")
    })
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID userId) {
        log.info("Deleting user {} by requester {}", userId, userPrincipal.getId());
        userService.deleteUser(userId, userPrincipal.getId());
        return ResponseEntity.noContent().build();
    }
}