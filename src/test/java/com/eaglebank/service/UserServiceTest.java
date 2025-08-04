package com.eaglebank.service;

import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.entity.User;
import com.eaglebank.exception.ResourceAlreadyExistsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;
    
    private CreateUserRequest createUserRequest;
    private User user;
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        createUserRequest = CreateUserRequest.builder()
                .email("john.doe@example.com")
                .password("SecurePass123!")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .address("123 Main St")
                .build();
        
        user = User.builder()
                .id(userId)
                .email("john.doe@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .address("123 Main St")
                .build();
    }
    
    @Test
    void createUser_ShouldCreateNewUser_WhenValidRequest() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        // When
        UserResponse response = userService.createUser(createUserRequest);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getId()).isEqualTo(userId);
        
        verify(userRepository).existsByEmail("john.doe@example.com");
        verify(passwordEncoder).encode("SecurePass123!");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void createUser_ShouldThrowException_WhenEmailAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(createUserRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("User already exists with email");
        
        verify(userRepository).existsByEmail("john.doe@example.com");
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // When
        UserResponse response = userService.getUserById(userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        
        verify(userRepository).findById(userId);
    }
    
    @Test
    void getUserById_ShouldThrowException_WhenUserNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id");
        
        verify(userRepository).findById(userId);
    }
    
    @Test
    void getUserByEmail_ShouldReturnUser_WhenUserExists() {
        // Given
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        
        // When
        User foundUser = userService.getUserByEmail("john.doe@example.com");
        
        // Then
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(foundUser.getId()).isEqualTo(userId);
        
        verify(userRepository).findByEmail("john.doe@example.com");
    }
    
    @Test
    void getUserByEmail_ShouldThrowException_WhenUserNotFound() {
        // Given
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> userService.getUserByEmail("john.doe@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with email");
        
        verify(userRepository).findByEmail("john.doe@example.com");
    }
}