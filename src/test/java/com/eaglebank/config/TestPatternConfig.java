package com.eaglebank.config;

import com.eaglebank.cache.CacheEvictionListener;
import com.eaglebank.cache.CacheStatisticsService;
import com.eaglebank.cache.CacheWarmingService;
import com.eaglebank.pattern.chain.AmountValidationHandler;
import com.eaglebank.pattern.chain.DescriptionValidationHandler;
import com.eaglebank.pattern.chain.TransactionTypeValidationHandler;
import com.eaglebank.pattern.chain.TransactionValidationChain;
import com.eaglebank.pattern.command.CommandInvoker;
import com.eaglebank.pattern.factory.*;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.pattern.strategy.*;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.UserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@TestConfiguration
public class TestPatternConfig {
    
    @Bean
    @Primary
    public TransactionStrategyFactory transactionStrategyFactory(
            List<TransactionStrategy> strategies,
            WithdrawalStrategy withdrawalStrategy,
            CreditWithdrawalStrategy creditWithdrawalStrategy,
            DepositStrategy depositStrategy,
            TransferStrategy transferStrategy) {
        return new TransactionStrategyFactory(strategies, withdrawalStrategy, creditWithdrawalStrategy, depositStrategy, transferStrategy);
    }
    
    @Bean
    public DepositStrategy depositStrategy() {
        return new DepositStrategy();
    }
    
    @Bean
    public WithdrawalStrategy withdrawalStrategy() {
        return new WithdrawalStrategy();
    }
    
    @Bean
    public CreditWithdrawalStrategy creditWithdrawalStrategy() {
        return new CreditWithdrawalStrategy();
    }
    
    @Bean
    public TransferStrategy transferStrategy() {
        return new TransferStrategy();
    }
    
    @Bean
    @Primary
    public AccountFactoryProvider accountFactoryProvider(List<AccountFactory> factories) {
        return new AccountFactoryProvider(factories);
    }
    
    @Bean
    public SavingsAccountFactory savingsAccountFactory() {
        return new SavingsAccountFactory();
    }
    
    @Bean
    public CheckingAccountFactory checkingAccountFactory() {
        return new CheckingAccountFactory();
    }
    
    @Bean
    public CreditAccountFactory creditAccountFactory() {
        return new CreditAccountFactory();
    }
    
    @Bean
    @Primary
    public TransactionValidationChain transactionValidationChain(
            AmountValidationHandler amountHandler,
            TransactionTypeValidationHandler typeHandler,
            DescriptionValidationHandler descriptionHandler) {
        return new TransactionValidationChain(amountHandler, typeHandler, descriptionHandler);
    }
    
    @Bean
    public AmountValidationHandler amountValidationHandler() {
        return new AmountValidationHandler();
    }
    
    @Bean
    public TransactionTypeValidationHandler transactionTypeValidationHandler() {
        return new TransactionTypeValidationHandler();
    }
    
    @Bean
    public DescriptionValidationHandler descriptionValidationHandler() {
        return new DescriptionValidationHandler();
    }
    
    @Bean
    @Primary
    public CommandInvoker commandInvoker() {
        return new CommandInvoker();
    }
    
    @Bean
    @Primary
    public EventPublisher eventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new EventPublisher(applicationEventPublisher);
    }
    
    
    
    
    @Bean
    @Primary
    public CacheWarmingService cacheWarmingService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            UserService userService,
            AccountService accountService) {
        return new CacheWarmingService(userRepository, accountRepository, userService, accountService);
    }
    
    @Bean
    @Primary
    public CacheStatisticsService cacheStatisticsService(CacheManager cacheManager) {
        return new CacheStatisticsService(cacheManager);
    }
    
    @Bean
    @Primary
    public CacheEvictionListener cacheEvictionListener(CacheStatisticsService statisticsService) {
        return new CacheEvictionListener(statisticsService);
    }
    
}