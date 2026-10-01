package com.nexusops.sla.controller;

import com.nexusops.sla.dto.*;
import com.nexusops.sla.service.SlaDefinitionService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sla/definitions")
@RequiredArgsConstructor
@Tag(name = "SLA Definitions", description = "SLA definition management endpoints")
public class SlaDefinitionController {

    private final SlaDefinitionService slaDefinitionService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('SLA', 'CREATE')")
    @Operation(summary = "Create a new SLA definition")
    public ResponseEntity<SlaDefinitionDto> createDefinition(@Valid @RequestBody CreateSlaDefinitionRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(slaDefinitionService.createDefinition(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List SLA definitions for current tenant")
    public ResponseEntity<List<SlaDefinitionDto>> listDefinitions(
            @RequestParam(required = false) Boolean active) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();

        if (Boolean.TRUE.equals(active)) {
            return ResponseEntity.ok(slaDefinitionService.findActiveByTenantId(tenantId));
        }
        return ResponseEntity.ok(slaDefinitionService.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get SLA definition by ID")
    public ResponseEntity<SlaDefinitionDto> getDefinition(@PathVariable String id) {
        return slaDefinitionService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'UPDATE')")
    @Operation(summary = "Update SLA definition")
    public ResponseEntity<SlaDefinitionDto> updateDefinition(
            @PathVariable String id,
            @Valid @RequestBody UpdateSlaDefinitionRequest request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(slaDefinitionService.updateDefinition(id, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'DELETE')")
    @Operation(summary = "Delete SLA definition")
    public ResponseEntity<Void> deleteDefinition(@PathVariable String id) {
        slaDefinitionService.deleteDefinition(id);
        return ResponseEntity.ok().build();
    }
}