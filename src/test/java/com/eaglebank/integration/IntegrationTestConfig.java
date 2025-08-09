package com.eaglebank.integration;

import com.eaglebank.config.TestRedisConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(TestRedisConfig.class)
public class IntegrationTestConfig {
    // This configuration imports the test Redis config for all integration tests
}