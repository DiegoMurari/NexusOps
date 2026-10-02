package com.nexusops.asset.controller;

import com.nexusops.asset.dto.CreateLocationRequest;
import com.nexusops.asset.dto.LocationDto;
import com.nexusops.asset.service.LocationService;
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
@RequestMapping("/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Asset location management endpoints")
public class LocationController {

    private final LocationService locationService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('ASSET', 'CREATE')")
    @Operation(summary = "Create a new location")
    public ResponseEntity<LocationDto> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(locationService.createLocation(request, tenantId, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List locations for current tenant")
    public ResponseEntity<List<LocationDto>> listLocations() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(locationService.findByTenantId(tenantId));
    }

    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Active locations (id, name, code) to pick from in a form; no asset permission needed")
    public ResponseEntity<List<LocationDto.Option>> options() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(locationService.findOptions(tenantId));
    }

    @GetMapping("/roots")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List root locations")
    public ResponseEntity<List<LocationDto>> listRoots() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(locationService.findRoots(tenantId));
    }

    @GetMapping("/{parentId}/children")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List child locations")
    public ResponseEntity<List<LocationDto>> listChildren(@PathVariable String parentId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(locationService.findChildren(parentId, tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Get location by ID")
    public ResponseEntity<LocationDto> getLocation(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(locationService.findByIdOrThrow(id, tenantId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'UPDATE')")
    @Operation(summary = "Update location")
    public ResponseEntity<LocationDto> updateLocation(@PathVariable String id, @Valid @RequestBody CreateLocationRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(locationService.updateLocation(id, tenantId, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'DELETE')")
    @Operation(summary = "Delete location")
    public ResponseEntity<Void> deleteLocation(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        locationService.deleteLocation(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
