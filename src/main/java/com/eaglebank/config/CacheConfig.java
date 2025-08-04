package com.eaglebank.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {
    
    public static final String USERS_CACHE = "users";
    public static final String ACCOUNTS_CACHE = "accounts";
    public static final String TRANSACTIONS_CACHE = "transactions";
    public static final String USER_ACCOUNTS_CACHE = "userAccounts";
    public static final String ACCOUNT_TRANSACTIONS_CACHE = "accountTransactions";
    
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
            USERS_CACHE,
            ACCOUNTS_CACHE,
            TRANSACTIONS_CACHE,
            USER_ACCOUNTS_CACHE,
            ACCOUNT_TRANSACTIONS_CACHE
        ));
        return cacheManager;
    }
}