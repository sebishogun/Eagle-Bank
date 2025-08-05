package com.eaglebank.service;

import com.eaglebank.dto.response.PasswordStrengthResponse;
import lombok.extern.slf4j.Slf4j;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.Configuration;
import me.gosimple.nbvcxz.resources.ConfigurationBuilder;
import me.gosimple.nbvcxz.resources.Dictionary;
import me.gosimple.nbvcxz.resources.DictionaryBuilder;
import me.gosimple.nbvcxz.scoring.Result;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PasswordStrengthService {
    
    private static final double MINIMUM_ENTROPY = 40.0; // Approximately 1 year to crack offline
    private static final String OFFLINE_BCRYPT_ATTACK = "offline_bcrypt";
    
    private final Nbvcxz nbvcxz;
    
    public PasswordStrengthService() {
        Configuration configuration = new ConfigurationBuilder()
                .setMinimumEntropy(MINIMUM_ENTROPY)
                .setLocale(Locale.US)
                .createConfiguration();
        
        this.nbvcxz = new Nbvcxz(configuration);
        log.info("PasswordStrengthService initialized with minimum entropy: {}", MINIMUM_ENTROPY);
    }
    
    /**
     * Validates password strength and returns detailed feedback
     */
    public PasswordStrengthResponse checkPasswordStrength(String password, String email, 
                                                         String firstName, String lastName) {
        // Create user-specific exclusion dictionary
        Configuration configuration = createUserSpecificConfiguration(email, firstName, lastName);
        Nbvcxz customNbvcxz = new Nbvcxz(configuration);
        
        // Estimate password strength
        Result result = customNbvcxz.estimate(password);
        
        // Build response
        return PasswordStrengthResponse.builder()
                .isAcceptable(result.isMinimumEntropyMet())
                .entropy(result.getEntropy())
                .score(calculateScore(result.getEntropy()))
                .estimatedCrackTime(getHumanReadableCrackTime(result))
                .suggestions(extractSuggestions(result))
                .warning(result.getFeedback() != null ? result.getFeedback().getWarning() : null)
                .build();
    }
    
    /**
     * Simple validation for password acceptance
     */
    public boolean isPasswordAcceptable(String password, String email, 
                                      String firstName, String lastName) {
        Configuration configuration = createUserSpecificConfiguration(email, firstName, lastName);
        Nbvcxz customNbvcxz = new Nbvcxz(configuration);
        Result result = customNbvcxz.estimate(password);
        return result.isMinimumEntropyMet();
    }
    
    /**
     * Creates a configuration with user-specific exclusions
     */
    private Configuration createUserSpecificConfiguration(String email, String firstName, String lastName) {
        // Build exclusion list from user data
        List<String> exclusions = new ArrayList<>();
        
        if (email != null && !email.isEmpty()) {
            exclusions.add(email.toLowerCase());
            // Add email username part
            String emailUsername = email.split("@")[0];
            exclusions.add(emailUsername.toLowerCase());
        }
        
        if (firstName != null && !firstName.isEmpty()) {
            exclusions.add(firstName.toLowerCase());
        }
        
        if (lastName != null && !lastName.isEmpty()) {
            exclusions.add(lastName.toLowerCase());
        }
        
        // Add common combinations
        if (firstName != null && lastName != null) {
            exclusions.add((firstName + lastName).toLowerCase());
            exclusions.add((lastName + firstName).toLowerCase());
            exclusions.add((firstName + "." + lastName).toLowerCase());
            exclusions.add((firstName.charAt(0) + lastName).toLowerCase());
        }
        
        // Create exclusion dictionary
        DictionaryBuilder dictBuilder = new DictionaryBuilder()
                .setDictionaryName("user-specific")
                .setExclusion(true);
        
        for (String word : exclusions) {
            dictBuilder.addWord(word, 0);
        }
        
        Dictionary exclusionDictionary = dictBuilder.createDictionary();
        
        return new ConfigurationBuilder()
                .setMinimumEntropy(MINIMUM_ENTROPY)
                .setLocale(Locale.US)
                .setDictionaries(Arrays.asList(exclusionDictionary))
                .createConfiguration();
    }
    
    /**
     * Converts entropy to a 0-4 score for easier understanding
     */
    private int calculateScore(double entropy) {
        if (entropy < 20) return 0; // Very weak
        if (entropy < 30) return 1; // Weak
        if (entropy < 40) return 2; // Fair
        if (entropy < 50) return 3; // Strong
        return 4; // Very strong
    }
    
    /**
     * Gets human-readable crack time estimate
     */
    private String getHumanReadableCrackTime(Result result) {
        BigDecimal seconds = result.getGuesses()
                .divide(BigDecimal.valueOf(1_000_000_000), BigDecimal.ROUND_HALF_UP); // BCrypt rate
        
        if (seconds.compareTo(BigDecimal.valueOf(60)) < 0) {
            return "less than a minute";
        } else if (seconds.compareTo(BigDecimal.valueOf(3600)) < 0) {
            long minutes = seconds.divide(BigDecimal.valueOf(60), BigDecimal.ROUND_DOWN).longValue();
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else if (seconds.compareTo(BigDecimal.valueOf(86400)) < 0) {
            long hours = seconds.divide(BigDecimal.valueOf(3600), BigDecimal.ROUND_DOWN).longValue();
            return hours + " hour" + (hours == 1 ? "" : "s");
        } else if (seconds.compareTo(BigDecimal.valueOf(2592000)) < 0) {
            long days = seconds.divide(BigDecimal.valueOf(86400), BigDecimal.ROUND_DOWN).longValue();
            return days + " day" + (days == 1 ? "" : "s");
        } else if (seconds.compareTo(BigDecimal.valueOf(31536000)) < 0) {
            long months = seconds.divide(BigDecimal.valueOf(2592000), BigDecimal.ROUND_DOWN).longValue();
            return months + " month" + (months == 1 ? "" : "s");
        } else {
            long years = seconds.divide(BigDecimal.valueOf(31536000), BigDecimal.ROUND_DOWN).longValue();
            if (years > 1000000) {
                return "centuries";
            }
            return years + " year" + (years == 1 ? "" : "s");
        }
    }
    
    /**
     * Extracts actionable suggestions from the result
     */
    private List<String> extractSuggestions(Result result) {
        List<String> suggestions = new ArrayList<>();
        
        if (result.getFeedback() != null && result.getFeedback().getSuggestion() != null) {
            suggestions.addAll(result.getFeedback().getSuggestion());
        }
        
        // Add custom suggestions based on entropy
        if (result.getEntropy() < MINIMUM_ENTROPY) {
            if (result.getEntropy() < 20) {
                suggestions.add("Try using a passphrase with multiple unrelated words");
            } else if (result.getEntropy() < 30) {
                suggestions.add("Add more unique characters or make your password longer");
            } else {
                suggestions.add("Your password is almost strong enough, try adding a few more characters");
            }
        }
        
        return suggestions;
    }
}