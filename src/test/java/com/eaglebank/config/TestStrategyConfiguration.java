package com.eaglebank.config;

import com.eaglebank.pattern.strategy.*;
import com.eaglebank.validation.AccountStatusTransitionValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

/**
 * Test configuration for strategy beans that are required in integration tests.
 */
@TestConfiguration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {"com.eaglebank.audit"})
public class TestStrategyConfiguration {
    
    @Bean
    public ActiveAccountStrategy activeAccountStrategy() {
        return new ActiveAccountStrategy();
    }
    
    @Bean
    public FrozenAccountStrategy frozenAccountStrategy() {
        return new FrozenAccountStrategy();
    }
    
    @Bean
    public ClosedAccountStrategy closedAccountStrategy() {
        return new ClosedAccountStrategy();
    }
    
    @Bean
    public InactiveAccountStrategy inactiveAccountStrategy() {
        return new InactiveAccountStrategy();
    }
    
    @Bean
    public AccountStatusStrategyFactory accountStatusStrategyFactory(List<AccountStatusStrategy> strategies) {
        return new AccountStatusStrategyFactory(strategies);
    }
    
    @Bean
    public AccountStatusTransitionValidator accountStatusTransitionValidator() {
        return new AccountStatusTransitionValidator();
    }
}