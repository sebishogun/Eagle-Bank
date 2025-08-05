package com.eaglebank.config;

import com.eaglebank.pattern.decorator.*;
import com.eaglebank.metrics.TransactionMetricsCollector;
import com.eaglebank.pattern.chain.TransactionValidationChain;
import com.eaglebank.service.TransactionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DecoratorConfig {
    
    @Bean
    public BaseTransactionProcessor baseTransactionProcessor(TransactionService transactionService) {
        return new BaseTransactionProcessor(transactionService);
    }
    
    @Bean
    public MetricsTransactionDecorator metricsTransactionDecorator(
            BaseTransactionProcessor baseProcessor,
            TransactionMetricsCollector metricsCollector) {
        return new MetricsTransactionDecorator(baseProcessor, metricsCollector);
    }
    
    @Bean
    public ValidationTransactionDecorator validationTransactionDecorator(
            MetricsTransactionDecorator metricsDecorator,
            TransactionValidationChain validationChain) {
        return new ValidationTransactionDecorator(metricsDecorator, validationChain);
    }
    
    @Bean
    public NotificationTransactionDecorator notificationTransactionDecorator(
            ValidationTransactionDecorator validationDecorator) {
        return new NotificationTransactionDecorator(validationDecorator);
    }
    
    @Bean
    @Primary
    public LoggingTransactionDecorator loggingTransactionDecorator(
            NotificationTransactionDecorator notificationDecorator) {
        return new LoggingTransactionDecorator(notificationDecorator);
    }
    
    @Bean
    @Primary
    public TransactionProcessor transactionProcessor(LoggingTransactionDecorator loggingDecorator) {
        return loggingDecorator;
    }
}