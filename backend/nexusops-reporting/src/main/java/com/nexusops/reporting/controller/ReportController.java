package com.nexusops.reporting.controller;

import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.*;
import com.nexusops.reporting.service.ReportDataService;
import com.nexusops.reporting.service.ReportService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Report definitions and built-in analytics")
public class ReportController {

    private final ReportService reportService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Create a report definition")
    public ResponseEntity<ReportDto> create(@Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.ok(reportService.create(request, tenant(), user()));
    }

    @GetMapping
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "List reports visible to the current user")
    public ResponseEntity<List<ReportDto>> list() {
        return ResponseEntity.ok(reportService.listVisible(tenant(), user()));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "Live ticket summary for the dashboard")
    public ResponseEntity<ReportResultDto> overview(@RequestParam(defaultValue = "" + ReportDataService.DEFAULT_DAYS) int days) {
        return ResponseEntity.ok(reportService.overview(tenant(), days));
    }

    @GetMapping("/builtin")
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "Run a built-in analysis directly (no saved report needed)")
    public ResponseEntity<ReportResultDto> builtin(
            @RequestParam Report.ReportType type,
            @RequestParam(defaultValue = "" + ReportDataService.DEFAULT_DAYS) int days) {
        return ResponseEntity.ok(reportService.runBuiltin(type, tenant(), days));
    }

    @GetMapping("/types")
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "List supported report types")
    public ResponseEntity<List<Report.ReportType>> types() {
        return ResponseEntity.ok(List.of(Report.ReportType.values()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "Get report by ID")
    public ResponseEntity<ReportDto> get(@PathVariable String id) {
        return ResponseEntity.ok(reportService.get(id, tenant(), user()));
    }

    @GetMapping("/{id}/run")
    @PreAuthorize("hasPermission('REPORT', 'READ')")
    @Operation(summary = "Execute a report")
    public ResponseEntity<ReportResultDto> run(@PathVariable String id,
                                               @RequestParam(defaultValue = "" + ReportDataService.DEFAULT_DAYS) int days) {
        return ResponseEntity.ok(reportService.run(id, tenant(), user(), days));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasPermission('REPORT', 'EXPORT')")
    @Operation(summary = "Export a report as CSV")
    public ResponseEntity<byte[]> export(@PathVariable String id,
                                         @RequestParam(defaultValue = "" + ReportDataService.DEFAULT_DAYS) int days) {
        String csv = reportService.exportCsv(id, tenant(), user(), days);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Update a report (owner only)")
    public ResponseEntity<ReportDto> update(@PathVariable String id, @Valid @RequestBody UpdateReportRequest request) {
        return ResponseEntity.ok(reportService.update(id, tenant(), user(), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('REPORT', 'CREATE')")
    @Operation(summary = "Delete a report (owner only)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        reportService.delete(id, tenant(), user());
        return ResponseEntity.ok().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String user() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
