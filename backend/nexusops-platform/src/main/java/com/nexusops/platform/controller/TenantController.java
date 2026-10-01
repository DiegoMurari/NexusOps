package com.nexusops.platform.controller;

import com.nexusops.platform.domain.Tenant;
import com.nexusops.platform.dto.*;
import com.nexusops.platform.service.TenantService;
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
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Multi-tenancy management endpoints")
public class TenantController {

    private final TenantService tenantService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new tenant")
    public ResponseEntity<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(tenantService.createTenant(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all tenants")
    public ResponseEntity<List<TenantDto>> listTenants(
            @RequestParam(required = false) Tenant.TenantStatus status) {
        if (status != null) {
            return ResponseEntity.ok(tenantService.findByStatus(status));
        }
        return ResponseEntity.ok(tenantService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TENANT', 'READ')")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<TenantDto> getTenant(@PathVariable String id) {
        return tenantService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/domain/{domain}")
    @PreAuthorize("hasPermission('TENANT', 'READ')")
    @Operation(summary = "Get tenant by domain")
    public ResponseEntity<TenantDto> getTenantByDomain(@PathVariable String domain) {
        return tenantService.findByDomain(domain)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('TENANT', 'UPDATE')")
    @Operation(summary = "Update tenant")
    public ResponseEntity<TenantDto> updateTenant(
            @PathVariable String id,
            @Valid @RequestBody UpdateTenantRequest request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(tenantService.updateTenant(id, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete tenant (soft delete)")
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.ok().build();
    }
}