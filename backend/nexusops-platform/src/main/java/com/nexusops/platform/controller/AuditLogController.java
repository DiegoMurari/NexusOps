package com.nexusops.platform.controller;

import com.nexusops.platform.dto.AuditLogDto;
import com.nexusops.platform.service.AuditService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Audit log query endpoints (tenant scoped)")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasPermission('AUDIT', 'READ')")
    @Operation(summary = "List audit logs for current tenant, newest first, with optional filters")
    public ResponseEntity<Page<AuditLogDto>> listAuditLogs(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String userId) {
        return ResponseEntity.ok(auditService.search(tenant(), action, resourceType, userId, since, cap(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('AUDIT', 'READ')")
    @Operation(summary = "Get audit log by ID")
    public ResponseEntity<AuditLogDto> getAuditLog(@PathVariable String id) {
        return ResponseEntity.ok(auditService.findById(tenant(), id));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasPermission('AUDIT', 'READ')")
    @Operation(summary = "List audit logs by user")
    public ResponseEntity<List<AuditLogDto>> listByUser(@PathVariable String userId) {
        return ResponseEntity.ok(auditService.findByUserId(tenant(), userId));
    }

    @GetMapping("/resource")
    @PreAuthorize("hasPermission('AUDIT', 'READ')")
    @Operation(summary = "List audit logs by resource")
    public ResponseEntity<List<AuditLogDto>> listByResource(
            @RequestParam String resourceType,
            @RequestParam String resourceId) {
        return ResponseEntity.ok(auditService.findByResource(tenant(), resourceType, resourceId));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List audit logs for specific tenant (SUPER_ADMIN)")
    public ResponseEntity<Page<AuditLogDto>> listByTenant(
            @PathVariable String tenantId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Instant since) {
        return ResponseEntity.ok(auditService.search(tenantId, null, null, null, since, cap(pageable)));
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    /** Page size is capped so a single request cannot dump the whole table. */
    private static Pageable cap(Pageable pageable) {
        return pageable.getPageSize() <= MAX_PAGE_SIZE ? pageable
            : PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
