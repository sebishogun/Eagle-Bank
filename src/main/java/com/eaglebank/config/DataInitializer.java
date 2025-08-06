package com.eaglebank.config;

import com.eaglebank.entity.User;
import com.eaglebank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional
    public void run(String... args) {
        initializeAdminUser();
    }
    
    private void initializeAdminUser() {
        String adminEmail = "admin@eaglebank.com";
        
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin123!"))
                    .firstName("System")
                    .lastName("Administrator")
                    .phoneNumber("+1234567890")
                    .address("Eagle Bank HQ")
                    .role(User.Role.ADMIN)
                    .build();
            
            userRepository.save(admin);
            log.info("Admin user created with email: {}", adminEmail);
        } else {
            log.info("Admin user already exists");
        }
    }
}