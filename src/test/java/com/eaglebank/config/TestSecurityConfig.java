package com.eaglebank.config;

import com.eaglebank.security.UserPrincipal;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.UUID;

@TestConfiguration
public class TestSecurityConfig {
    
    @Retention(RetentionPolicy.RUNTIME)
    @WithSecurityContext(factory = WithMockUserPrincipalSecurityContextFactory.class)
    public @interface WithMockUserPrincipal {
        String userId() default "019875d7-0000-0000-0000-000000000001";
        String email() default "test@example.com";
    }
    
    public static class WithMockUserPrincipalSecurityContextFactory 
            implements WithSecurityContextFactory<WithMockUserPrincipal> {
        
        @Override
        public SecurityContext createSecurityContext(WithMockUserPrincipal annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            
            UserPrincipal principal = new UserPrincipal(
                UUID.fromString(annotation.userId()),
                annotation.email(),
                "password",
                Collections.emptyList()
            );
            
            Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, 
                "password", 
                principal.getAuthorities()
            );
            
            context.setAuthentication(auth);
            return context;
        }
    }
}