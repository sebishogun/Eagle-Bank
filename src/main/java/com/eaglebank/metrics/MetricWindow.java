package com.eaglebank.metrics;

import lombok.Getter;

@Getter
public enum MetricWindow {
    ONE_MINUTE(60),
    FIVE_MINUTES(300),
    ONE_HOUR(3600),
    TWENTY_FOUR_HOURS(86400);
    
    private final int seconds;
    
    MetricWindow(int seconds) {
        this.seconds = seconds;
    }
    
    public long getMillis() {
        return seconds * 1000L;
    }
}