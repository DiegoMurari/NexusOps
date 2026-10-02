package com.nexusops.asset.controller;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.AssetHistoryDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import com.nexusops.asset.dto.LinkedAssetDto;
import com.nexusops.asset.dto.LinkedTicketDto;
import com.nexusops.asset.dto.UpdateAssetRequest;
import com.nexusops.asset.service.AssetHistoryService;
import com.nexusops.asset.service.AssetService;
import com.nexusops.asset.service.AssetTicketLinkService;
import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "CMDB asset management endpoints")
public class AssetController {

    private final AssetService assetService;
    private final AssetHistoryService historyService;
    private final AssetTicketLinkService linkService;
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

    @GetMapping("/{id}/history")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Change history of an asset, newest first")
    public ResponseEntity<Page<AssetHistoryDto>> history(
            @PathVariable String id,
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        Pageable capped = pageable.getPageSize() <= 100 ? pageable : PageRequest.of(pageable.getPageNumber(), 100);
        return ResponseEntity.ok(historyService.list(tenantId, id, capped));
    }

    @GetMapping("/{id}/tickets")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Tickets linked to an asset")
    public ResponseEntity<List<LinkedTicketDto>> linkedTickets(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(linkService.forAsset(id, tenantId));
    }

    @PutMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Link a ticket to an asset (idempotent)")
    public ResponseEntity<LinkedTicketDto> linkTicket(@PathVariable String id, @PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String actor = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(linkService.link(id, ticketId, tenantId, actor));
    }

    @DeleteMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Unlink a ticket from an asset")
    public ResponseEntity<Void> unlinkTicket(@PathVariable String id, @PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String actor = securityUtils.getCurrentUserId().orElseThrow();
        linkService.unlink(id, ticketId, tenantId, actor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Active users of the tenant that can be set as asset owner")
    public ResponseEntity<List<UserDirectory.UserRef>> assignees(@RequestParam(defaultValue = "") String q) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(assetService.assignableUsers(tenantId, q));
    }

    @GetMapping("/by-ticket/{ticketId}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Assets linked to a ticket")
    public ResponseEntity<List<LinkedAssetDto>> assetsOfTicket(@PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(linkService.forTicket(ticketId, tenantId));
    }

    @GetMapping("/stats/count")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Count assets for current tenant")
    public ResponseEntity<Long> countAssets() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(assetService.countByTenantId(tenantId));
    }
}
