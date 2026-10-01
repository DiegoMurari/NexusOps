package com.nexusops.asset.controller;

import com.nexusops.asset.dto.CIRelationshipDto;
import com.nexusops.asset.dto.CreateCIRelationshipRequest;
import com.nexusops.asset.service.CIRelationshipService;
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
@RequestMapping("/ci-relationships")
@RequiredArgsConstructor
@Tag(name = "CI Relationships", description = "Configuration item relationship endpoints")
public class CIRelationshipController {

    private final CIRelationshipService relationshipService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('ASSET', 'CREATE')")
    @Operation(summary = "Create a relationship between two assets")
    public ResponseEntity<CIRelationshipDto> createRelationship(@Valid @RequestBody CreateCIRelationshipRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(relationshipService.createRelationship(request, tenantId, createdBy));
    }

    @GetMapping("/asset/{assetId}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List relationships for an asset")
    public ResponseEntity<List<CIRelationshipDto>> listByAsset(@PathVariable String assetId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(relationshipService.findByAssetId(assetId, tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'DELETE')")
    @Operation(summary = "Delete a relationship")
    public ResponseEntity<Void> deleteRelationship(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        relationshipService.deleteRelationship(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
