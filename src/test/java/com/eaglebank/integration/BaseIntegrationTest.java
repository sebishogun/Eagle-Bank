package com.eaglebank.integration;

import com.eaglebank.config.TestStrategyConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all integration tests.
 * Provides common configuration including mock Redis for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestConfig.class)
@ContextConfiguration(classes = {TestStrategyConfiguration.class})
public abstract class BaseIntegrationTest {
    // Common setup for all integration tests
}