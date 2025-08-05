package com.eaglebank.controller;

import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.AuditRepository;
import com.eaglebank.exception.ErrorResponse;
import com.eaglebank.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit trail query endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditRepository auditRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all audit entries",
              description = "Retrieves paginated audit entries. Admin access only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AuditEntry>> getAllAuditEntries(
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "createdAt,desc") Pageable pageable) {
        
        log.info("Admin retrieving all audit entries");
        Page<AuditEntry> entries = auditRepository.findAll(pageable);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user's audit trail",
              description = "Retrieves audit entries for a specific user. Users can only view their own audit trail unless admin.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - cannot view other user's audit trail",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AuditEntry>> getUserAuditEntries(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "createdAt,desc") Pageable pageable) {
        
        // Check if user is accessing their own audit trail or is admin
        if (!userPrincipal.getId().equals(userId) && !userPrincipal.hasRole("ADMIN")) {
            log.warn("User {} attempted to access audit trail of user {}", userPrincipal.getId(), userId);
            return ResponseEntity.status(403).build();
        }
        
        log.info("Retrieving audit entries for user: {}", userId);
        Page<AuditEntry> entries = auditRepository.findByUserId(userId, pageable);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get account audit trail",
              description = "Retrieves audit entries for a specific account. Admin access only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AuditEntry>> getAccountAuditEntries(
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "createdAt,desc") Pageable pageable) {
        
        log.info("Admin retrieving audit entries for account: {}", accountId);
        Page<AuditEntry> entries = auditRepository.findByEntityTypeAndEntityId("Account", accountId.toString(), pageable);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/actions/{action}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit entries by action",
              description = "Retrieves audit entries for a specific action type. Admin access only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AuditEntry>> getAuditEntriesByAction(
            @Parameter(description = "Audit action", required = true)
            @PathVariable AuditEntry.AuditAction action,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "createdAt,desc") Pageable pageable) {
        
        log.info("Admin retrieving audit entries for action: {}", action);
        Page<AuditEntry> entries = auditRepository.findByAction(action, pageable);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit entries by date range",
              description = "Retrieves audit entries within a specific date range. Admin access only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit entries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = List.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AuditEntry>> getAuditEntriesByDateRange(
            @Parameter(description = "Start date/time", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End date/time", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        log.info("Admin retrieving audit entries between {} and {}", start, end);
        List<AuditEntry> entries = auditRepository.findByCreatedAtBetween(start, end);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get audit statistics",
              description = "Retrieves audit statistics for monitoring. Admin access only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuditStats> getAuditStatistics(
            @Parameter(description = "Start date/time for statistics")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "End date/time for statistics")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        if (start == null) start = LocalDateTime.now().minusDays(30);
        if (end == null) end = LocalDateTime.now();
        
        log.info("Admin retrieving audit statistics between {} and {}", start, end);
        
        AuditStats stats = AuditStats.builder()
                .totalEntries(auditRepository.count())
                .loginCount(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.LOGIN, start, end))
                .createCount(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.CREATE, start, end))
                .updateCount(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.UPDATE, start, end))
                .deleteCount(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.DELETE, start, end))
                .accessDeniedCount(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.ACCESS_DENIED, start, end))
                .startDate(start)
                .endDate(end)
                .build();
        
        return ResponseEntity.ok(stats);
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditStats {
        private long totalEntries;
        private long loginCount;
        private long createCount;
        private long updateCount;
        private long deleteCount;
        private long accessDeniedCount;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }
}