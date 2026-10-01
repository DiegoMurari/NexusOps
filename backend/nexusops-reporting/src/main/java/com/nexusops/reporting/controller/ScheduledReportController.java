package com.nexusops.reporting.controller;

import com.nexusops.reporting.dto.CreateScheduledReportRequest;
import com.nexusops.reporting.dto.ScheduledReportDto;
import com.nexusops.reporting.dto.UpdateScheduledReportRequest;
import com.nexusops.reporting.service.ScheduledReportService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scheduled-reports")
@RequiredArgsConstructor
@Tag(name = "Scheduled Reports", description = "Report schedule definitions")
public class ScheduledReportController {

    private final ScheduledReportService service;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Create a schedule")
    public ResponseEntity<ScheduledReportDto> create(@Valid @RequestBody CreateScheduledReportRequest request) {
        return ResponseEntity.ok(service.create(request, tenant(), user()));
    }

    @GetMapping
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "List schedules")
    public ResponseEntity<List<ScheduledReportDto>> list() {
        return ResponseEntity.ok(service.list(tenant(), user()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Activate/deactivate a schedule")
    public ResponseEntity<ScheduledReportDto> update(@PathVariable String id,
                                                     @Valid @RequestBody UpdateScheduledReportRequest request) {
        return ResponseEntity.ok(service.update(id, tenant(), user(), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Delete a schedule")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id, tenant(), user());
        return ResponseEntity.ok().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String user() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
