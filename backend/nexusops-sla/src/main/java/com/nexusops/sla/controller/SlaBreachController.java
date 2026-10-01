package com.nexusops.sla.controller;

import com.nexusops.sla.dto.SlaBreachDto;
import com.nexusops.sla.dto.SlaComplianceDto;
import com.nexusops.sla.service.SlaBreachService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/sla/breaches")
@RequiredArgsConstructor
@Tag(name = "SLA Breaches", description = "SLA breach management endpoints")
public class SlaBreachController {

    private final SlaBreachService slaBreachService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List SLA breaches for current tenant")
    public ResponseEntity<Page<SlaBreachDto>> listBreaches(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end) {
        
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();

        if (start != null && end != null) {
            // Would need to implement paginated version
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.ok(slaBreachService.findByTenantId(tenantId, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List active (unresolved) SLA breaches")
    public ResponseEntity<SlaBreachDto[]> listActiveBreaches() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(slaBreachService.findActiveBreaches(tenantId).toArray(new SlaBreachDto[0]));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get SLA breach by ID")
    public ResponseEntity<SlaBreachDto> getBreach(@PathVariable String id) {
        return slaBreachService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ticket/{ticketId}")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List SLA breaches for a ticket")
    public ResponseEntity<SlaBreachDto[]> getBreachesByTicket(@PathVariable String ticketId) {
        return ResponseEntity.ok(slaBreachService.findByTicketId(ticketId).toArray(new SlaBreachDto[0]));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasPermission('SLA', 'UPDATE')")
    @Operation(summary = "Acknowledge SLA breach")
    public ResponseEntity<SlaBreachDto> acknowledgeBreach(@PathVariable String id) {
        String acknowledgedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(slaBreachService.acknowledgeBreach(id, acknowledgedBy));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasPermission('SLA', 'UPDATE')")
    @Operation(summary = "Resolve SLA breach")
    public ResponseEntity<SlaBreachDto> resolveBreach(@PathVariable String id) {
        return ResponseEntity.ok(slaBreachService.resolveBreach(id));
    }

    @GetMapping("/compliance")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get SLA compliance report")
    public ResponseEntity<SlaComplianceDto> getComplianceReport(
            @RequestParam Instant start,
            @RequestParam Instant end) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(slaBreachService.getComplianceReport(tenantId, start, end));
    }
}