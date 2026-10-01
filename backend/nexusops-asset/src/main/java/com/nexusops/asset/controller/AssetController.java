package com.nexusops.asset.controller;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import com.nexusops.asset.dto.UpdateAssetRequest;
import com.nexusops.asset.service.AssetService;
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
@RequestMapping("/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "CMDB asset management endpoints")
public class AssetController {

    private final AssetService assetService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('ASSET', 'CREATE')")
    @Operation(summary = "Create a new asset")
    public ResponseEntity<AssetDto> createAsset(@Valid @RequestBody CreateAssetRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(assetService.createAsset(request, tenantId, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List assets with pagination and filters")
    public ResponseEntity<Page<AssetDto>> listAssets(
            @RequestParam(required = false) Asset.AssetType type,
            @RequestParam(required = false) Asset.LifecycleStatus status,
            @RequestParam(required = false) String assignedToId,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(assetService.list(tenantId, type, status, assignedToId, locationId, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<AssetDto> getAsset(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(assetService.findByIdOrThrow(id, tenantId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Update asset")
    public ResponseEntity<AssetDto> updateAsset(@PathVariable String id, @Valid @RequestBody UpdateAssetRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(assetService.updateAsset(id, tenantId, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'DELETE')")
    @Operation(summary = "Delete asset")
    public ResponseEntity<Void> deleteAsset(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        assetService.deleteAsset(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats/count")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Count assets for current tenant")
    public ResponseEntity<Long> countAssets() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(assetService.countByTenantId(tenantId));
    }
}
