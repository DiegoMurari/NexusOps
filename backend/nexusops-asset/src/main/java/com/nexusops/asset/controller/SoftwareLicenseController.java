package com.nexusops.asset.controller;

import com.nexusops.asset.domain.SoftwareLicense;
import com.nexusops.asset.dto.CreateSoftwareLicenseRequest;
import com.nexusops.asset.dto.SoftwareLicenseDto;
import com.nexusops.asset.dto.UpdateSoftwareLicenseRequest;
import com.nexusops.asset.service.SoftwareLicenseService;
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

@RestController
@RequestMapping("/software-licenses")
@RequiredArgsConstructor
@Tag(name = "Software Licenses", description = "Software license and compliance endpoints")
public class SoftwareLicenseController {

    private final SoftwareLicenseService licenseService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('ASSET', 'CREATE')")
    @Operation(summary = "Create a new software license")
    public ResponseEntity<SoftwareLicenseDto> createLicense(@Valid @RequestBody CreateSoftwareLicenseRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(licenseService.createLicense(request, tenantId, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List software licenses")
    public ResponseEntity<Page<SoftwareLicenseDto>> listLicenses(
            @RequestParam(required = false) SoftwareLicense.ComplianceStatus complianceStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(licenseService.list(tenantId, complianceStatus, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Get software license by ID")
    public ResponseEntity<SoftwareLicenseDto> getLicense(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(licenseService.findByIdOrThrow(id, tenantId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Update software license")
    public ResponseEntity<SoftwareLicenseDto> updateLicense(@PathVariable String id, @Valid @RequestBody UpdateSoftwareLicenseRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(licenseService.updateLicense(id, tenantId, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'DELETE')")
    @Operation(summary = "Delete software license")
    public ResponseEntity<Void> deleteLicense(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        licenseService.deleteLicense(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
