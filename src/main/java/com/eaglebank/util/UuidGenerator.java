package com.eaglebank.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public class UuidGenerator {
    
    private UuidGenerator() {
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Generates a UUID v7 (time-ordered) for entity IDs.
     * These UUIDs are time-sortable and provide better database indexing performance.
     * 
     * @return A time-ordered UUID v7
     */
    public static UUID generateUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
    
    /**
     * Generates a UUID v4 (random) for tokens and where complete randomness is needed.
     * 
     * @return A random UUID v4
     */
    public static UUID generateUuidV4() {
        return UUID.randomUUID();
    }
    
    /**
     * Parses a UUID from a string, handling null values.
     * 
     * @param uuidString The UUID string to parse
     * @return The parsed UUID or null if input is null/empty
     */
    public static UUID fromString(String uuidString) {
        if (uuidString == null || uuidString.trim().isEmpty()) {
            return null;
        }
        return UUID.fromString(uuidString);
    }
}