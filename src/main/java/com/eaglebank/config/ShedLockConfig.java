package com.eaglebank.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for ShedLock to ensure scheduled tasks run only once
 * in a clustered environment.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
@ConditionalOnProperty(
    value = "eagle-bank.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ShedLockConfig {
    
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Use database time for consistency
                .build()
        );
    }
}