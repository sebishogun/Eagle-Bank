package com.eaglebank.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicrometerConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "eagle-bank")
                .meterFilter(MeterFilter.deny(id -> {
                    String uri = id.getTag("uri");
                    return uri != null && (
                            uri.startsWith("/actuator") ||
                            uri.startsWith("/v3/api-docs") ||
                            uri.startsWith("/swagger")
                    );
                }));
    }
}