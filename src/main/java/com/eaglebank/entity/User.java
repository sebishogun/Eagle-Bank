package com.eaglebank.entity;

import com.eaglebank.util.UuidGenerator;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false, length = 100)
    private String firstName;
    
    @Column(nullable = false, length = 100)
    private String lastName;
    
    @Column(length = 20)
    private String phoneNumber;
    
    @Column(length = 500)
    private String address;
    
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'USER'")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;
    
    @Column(name = "security_version", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @Builder.Default
    private Integer securityVersion = 0;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    // in the future it would be an entity with many-to-many relationship
    public enum Role {
        USER,
        ADMIN
    }
    
    @PrePersist
    private void generateId() {
        if (id == null) {
            id = UuidGenerator.generateUuidV7();
        }
        super.onCreate();
    }
}