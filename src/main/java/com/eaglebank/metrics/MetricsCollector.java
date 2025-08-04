package com.eaglebank.metrics;

import java.util.Map;

public interface MetricsCollector {
    
    String getMetricName();
    
    Map<String, Object> collect();
    
    void reset();
}