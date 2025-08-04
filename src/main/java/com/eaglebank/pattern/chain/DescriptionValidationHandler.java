package com.eaglebank.pattern.chain;

import com.eaglebank.dto.request.CreateTransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DescriptionValidationHandler extends ValidationHandler<CreateTransactionRequest> {
    
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final String SUSPICIOUS_PATTERN = "(?i).*(hack|steal|launder|illegal).*";
    
    @Override
    protected boolean canHandle(CreateTransactionRequest request) {
        return request.getDescription() != null;
    }
    
    @Override
    protected void doValidate(CreateTransactionRequest request) {
        String description = request.getDescription();
        
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        
        if (description.matches(SUSPICIOUS_PATTERN)) {
            log.warn("Suspicious description detected: {}", description);
            throw new IllegalArgumentException("Transaction description contains prohibited content");
        }
        
        log.debug("Description validation passed");
    }
}