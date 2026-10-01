package com.nexusops.asset.controller;

import com.nexusops.asset.dto.CreateDiscoveryJobRequest;
import com.nexusops.asset.dto.DiscoveryJobDto;
import com.nexusops.asset.service.DiscoveryJobService;
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
@RequestMapping("/discovery-jobs")
@RequiredArgsConstructor
@Tag(name = "Discovery Jobs", description = "Asset discovery job configuration endpoints")
public class DiscoveryJobController {

    private final DiscoveryJobService discoveryJobService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('ASSET', 'CREATE')")
    @Operation(summary = "Create a new discovery job configuration")
    public ResponseEntity<DiscoveryJobDto> createJob(@Valid @RequestBody CreateDiscoveryJobRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(discoveryJobService.createJob(request, tenantId, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "List discovery jobs for current tenant")
    public ResponseEntity<List<DiscoveryJobDto>> listJobs() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(discoveryJobService.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'READ')")
    @Operation(summary = "Get discovery job by ID")
    public ResponseEntity<DiscoveryJobDto> getJob(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(discoveryJobService.findByIdOrThrow(id, tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ASSET', 'DELETE')")
    @Operation(summary = "Delete discovery job")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        discoveryJobService.deleteJob(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
