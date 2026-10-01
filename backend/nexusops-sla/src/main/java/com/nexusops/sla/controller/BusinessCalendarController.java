package com.nexusops.sla.controller;

import com.nexusops.sla.domain.BusinessCalendar;
import com.nexusops.sla.dto.BusinessCalendarDto;
import com.nexusops.sla.service.BusinessCalendarService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sla/calendars")
@RequiredArgsConstructor
@Tag(name = "Business Calendars", description = "Business calendar management endpoints")
public class BusinessCalendarController {

    private final BusinessCalendarService businessCalendarService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('SLA', 'CREATE')")
    @Operation(summary = "Create a new business calendar")
    public ResponseEntity<BusinessCalendarDto> createCalendar(@RequestBody BusinessCalendar calendar) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(businessCalendarService.createCalendar(calendar, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List business calendars for current tenant")
    public ResponseEntity<List<BusinessCalendarDto>> listCalendars() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(businessCalendarService.findByTenantId(tenantId));
    }

    @GetMapping("/default")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get default business calendar for current tenant")
    public ResponseEntity<BusinessCalendarDto> getDefaultCalendar() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return businessCalendarService.findDefaultByTenantId(tenantId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get business calendar by ID")
    public ResponseEntity<BusinessCalendarDto> getCalendar(@PathVariable String id) {
        return businessCalendarService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'UPDATE')")
    @Operation(summary = "Update business calendar")
    public ResponseEntity<BusinessCalendarDto> updateCalendar(
            @PathVariable String id,
            @RequestBody BusinessCalendar calendar) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(businessCalendarService.updateCalendar(id, calendar, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'DELETE')")
    @Operation(summary = "Delete business calendar")
    public ResponseEntity<Void> deleteCalendar(@PathVariable String id) {
        businessCalendarService.deleteCalendar(id);
        return ResponseEntity.ok().build();
    }
}