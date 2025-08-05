package com.eaglebank.service;

import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.request.UpdateUserRequest;
import com.eaglebank.dto.request.ChangePasswordRequest;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.User;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.ResourceAlreadyExistsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordStrengthService passwordStrengthService;
    
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Creating new user with email: {}", request.getEmail());
        
        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("User", "email", request.getEmail());
        }
        
        // Create new user entity
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .build();
        
        // Save user
        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());
        
        return mapToUserResponse(savedUser);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        log.debug("Fetching user with id: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        return mapToUserResponse(user);
    }
    
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        log.debug("Fetching user with email: {}", email);
        
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
    
    @Transactional
    public UserResponse updateUser(UUID userId, UUID requesterId, UpdateUserRequest request) {
        log.debug("Updating user with id: {} by requester: {}", userId, requesterId);
        
        // Check if requester is the same as the user being updated
        if (!userId.equals(requesterId)) {
            throw new ForbiddenException("Users can only update their own information");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Update fields only if they are provided (not null)
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        User updatedUser = userRepository.save(user);
        log.info("User {} updated successfully", userId);
        
        return mapToUserResponse(updatedUser);
    }
    
    @Transactional
    public void deleteUser(UUID userId, UUID requesterId) {
        log.debug("Deleting user with id: {} by requester: {}", userId, requesterId);
        
        // Check if requester is the same as the user being deleted
        if (!userId.equals(requesterId)) {
            throw new ForbiddenException("Users can only delete their own account");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Check if user has any accounts
        long accountCount = accountRepository.countByUserId(userId);
        if (accountCount > 0) {
            throw new IllegalStateException(
                String.format("Cannot delete user with %d existing account(s). Please delete all accounts first.", accountCount)
            );
        }
        
        userRepository.delete(user);
        log.info("User {} deleted successfully", userId);
    }
    
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
    
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        log.debug("Changing password for user: {}", email);
        
        // Validate password confirmation matches
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
        
        // Get user
        User user = getUserByEmail(email);
        
        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        
        // Check password strength
        boolean isAcceptable = passwordStrengthService.isPasswordAcceptable(
                request.getNewPassword(), 
                user.getEmail(), 
                user.getFirstName(), 
                user.getLastName()
        );
        
        if (!isAcceptable) {
            throw new IllegalArgumentException("New password does not meet security requirements. " +
                    "Please use a stronger password with more entropy.");
        }
        
        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("Password changed successfully for user: {}", email);
    }
}