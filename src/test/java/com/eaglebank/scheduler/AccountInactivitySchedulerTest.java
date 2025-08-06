package com.eaglebank.scheduler;

import com.eaglebank.service.AccountInactivityService;
import com.eaglebank.service.AccountInactivityService.AccountActivityStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountInactivitySchedulerTest {

    @Mock
    private AccountInactivityService accountInactivityService;

    @InjectMocks
    private AccountInactivityScheduler accountInactivityScheduler;

    private AccountActivityStatistics beforeStats;
    private AccountActivityStatistics afterStats;

    @BeforeEach
    void setUp() {
        beforeStats = AccountActivityStatistics.builder()
                .totalAccounts(100)
                .activeAccounts(70)
                .inactiveAccounts(20)
                .frozenAccounts(5)
                .closedAccounts(5)
                .inactivityThresholdDays(180)
                .build();

        afterStats = AccountActivityStatistics.builder()
                .totalAccounts(100)
                .activeAccounts(65)
                .inactiveAccounts(25)
                .frozenAccounts(5)
                .closedAccounts(5)
                .inactivityThresholdDays(180)
                .build();
    }

    @Test
    void checkAccountInactivity_ShouldCallServiceAndLogStatistics() {
        // Arrange
        when(accountInactivityService.getActivityStatistics())
                .thenReturn(beforeStats)
                .thenReturn(afterStats);
        when(accountInactivityService.checkAndMarkInactiveAccounts()).thenReturn(5);

        // Act
        accountInactivityScheduler.checkAccountInactivity();

        // Assert
        verify(accountInactivityService, times(2)).getActivityStatistics();
        verify(accountInactivityService).checkAndMarkInactiveAccounts();
    }

    @Test
    void checkAccountInactivity_ShouldHandleExceptions() {
        // Arrange
        when(accountInactivityService.getActivityStatistics())
                .thenThrow(new RuntimeException("Database error"));

        // Act - should not throw exception
        accountInactivityScheduler.checkAccountInactivity();

        // Assert
        verify(accountInactivityService).getActivityStatistics();
        verify(accountInactivityService, never()).checkAndMarkInactiveAccounts();
    }

    @Test
    void generateInactivityReport_ShouldGenerateReportWithStatistics() {
        // Arrange
        AccountActivityStatistics stats = AccountActivityStatistics.builder()
                .totalAccounts(100)
                .activeAccounts(75)
                .inactiveAccounts(15)
                .frozenAccounts(5)
                .closedAccounts(5)
                .inactivityThresholdDays(180)
                .build();
        
        when(accountInactivityService.getActivityStatistics()).thenReturn(stats);

        // Act
        accountInactivityScheduler.generateInactivityReport();

        // Assert
        verify(accountInactivityService).getActivityStatistics();
    }

    @Test
    void generateInactivityReport_ShouldHandleHighInactivePercentage() {
        // Arrange
        AccountActivityStatistics highInactiveStats = AccountActivityStatistics.builder()
                .totalAccounts(100)
                .activeAccounts(60)
                .inactiveAccounts(30) // 30% inactive
                .frozenAccounts(5)
                .closedAccounts(5)
                .inactivityThresholdDays(180)
                .build();
        
        when(accountInactivityService.getActivityStatistics()).thenReturn(highInactiveStats);

        // Act
        accountInactivityScheduler.generateInactivityReport();

        // Assert
        verify(accountInactivityService).getActivityStatistics();
    }

    @Test
    void generateInactivityReport_ShouldHandleZeroAccounts() {
        // Arrange
        AccountActivityStatistics emptyStats = AccountActivityStatistics.builder()
                .totalAccounts(0)
                .activeAccounts(0)
                .inactiveAccounts(0)
                .frozenAccounts(0)
                .closedAccounts(0)
                .inactivityThresholdDays(180)
                .build();
        
        when(accountInactivityService.getActivityStatistics()).thenReturn(emptyStats);

        // Act
        accountInactivityScheduler.generateInactivityReport();

        // Assert
        verify(accountInactivityService).getActivityStatistics();
    }

    @Test
    void generateInactivityReport_ShouldHandleExceptions() {
        // Arrange
        when(accountInactivityService.getActivityStatistics())
                .thenThrow(new RuntimeException("Database error"));

        // Act - should not throw exception
        accountInactivityScheduler.generateInactivityReport();

        // Assert
        verify(accountInactivityService).getActivityStatistics();
    }

    @Test
    void checkAccountInactivity_ShouldLogExecutionTime() {
        // Arrange
        when(accountInactivityService.getActivityStatistics())
                .thenReturn(beforeStats)
                .thenReturn(afterStats);
        when(accountInactivityService.checkAndMarkInactiveAccounts()).thenReturn(0);

        // Act
        accountInactivityScheduler.checkAccountInactivity();

        // Assert
        verify(accountInactivityService).checkAndMarkInactiveAccounts();
        // Execution time should be logged (verified through logs)
    }

    @Test
    void checkAccountInactivity_ShouldContinueEvenWhenServiceFails() {
        // Arrange
        when(accountInactivityService.getActivityStatistics()).thenReturn(beforeStats);
        when(accountInactivityService.checkAndMarkInactiveAccounts())
                .thenThrow(new RuntimeException("Processing error"));

        // Act - should not throw exception
        accountInactivityScheduler.checkAccountInactivity();

        // Assert
        verify(accountInactivityService).checkAndMarkInactiveAccounts();
        // Exception should be caught and logged
    }
}