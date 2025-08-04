package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateUserRequest;
import com.eaglebank.dto.response.UserResponse;
import com.eaglebank.exception.ResourceAlreadyExistsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockitoBean
    private UserService userService;
    
    private CreateUserRequest createUserRequest;
    private UserResponse userResponse;
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
        
        userResponse = UserResponse.builder()
                .id(userId)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .address("123 Main St")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void createUser_ShouldReturnCreatedStatus_WhenValidRequest() throws Exception {
        // Given
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(userResponse);
        
        // When & Then
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }
    
    @Test
    void createUser_ShouldReturnBadRequest_WhenInvalidEmail() throws Exception {
        // Given
        createUserRequest.setEmail("invalid-email");
        
        // When & Then
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void createUser_ShouldReturnBadRequest_WhenMissingRequiredFields() throws Exception {
        // Given
        CreateUserRequest invalidRequest = CreateUserRequest.builder()
                .email("john.doe@example.com")
                // Missing password, firstName, lastName
                .build();
        
        // When & Then
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void createUser_ShouldReturnConflict_WhenEmailAlreadyExists() throws Exception {
        // Given
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new ResourceAlreadyExistsException("User", "email", "john.doe@example.com"));
        
        // When & Then
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isConflict());
    }
    
    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() throws Exception {
        // Given
        when(userService.getUserById(userId)).thenReturn(userResponse);
        
        // When & Then
        mockMvc.perform(get("/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }
    
    @Test
    void getUserById_ShouldReturnNotFound_WhenUserDoesNotExist() throws Exception {
        // Given
        when(userService.getUserById(userId))
                .thenThrow(new ResourceNotFoundException("User", "id", userId));
        
        // When & Then
        mockMvc.perform(get("/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void getUserById_ShouldReturnBadRequest_WhenInvalidUUID() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/users/{userId}", "invalid-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}