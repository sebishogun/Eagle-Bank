package com.eaglebank.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UserLoggedInEvent extends AbstractDomainEvent {
    
    private final UUID userId;
    private final String username;
    private final String ipAddress;
    private final String userAgent;
    
    public UserLoggedInEvent(UUID userId, String username, String ipAddress, String userAgent) {
        super();
        this.userId = userId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
    
    @Override
    public String getEventType() {
        return "USER_LOGGED_IN";
    }
}