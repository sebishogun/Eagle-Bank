package com.eaglebank.pattern.factory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccountFactoryProvider {
    
    private final List<AccountFactory> factories;
    private Map<String, AccountFactory> factoryMap;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        AccountFactory::getAccountType,
                        Function.identity()
                ));
    }
    
    public AccountFactory getFactory(String accountType) {
        AccountFactory factory = factoryMap.get(accountType.toUpperCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown account type: " + accountType);
        }
        return factory;
    }
    
    public List<String> getAvailableAccountTypes() {
        return factories.stream()
                .map(AccountFactory::getAccountType)
                .collect(Collectors.toList());
    }
}