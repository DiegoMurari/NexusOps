package com.nexusops.platform.controller;

import com.nexusops.platform.dto.*;
import com.nexusops.platform.service.FeatureFlagService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/feature-flags")
@RequiredArgsConstructor
@Tag(name = "Feature Flags", description = "Feature flag management endpoints")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'CREATE')")
    @Operation(summary = "Create a new feature flag")
    public ResponseEntity<FeatureFlagDto> createFeatureFlag(@Valid @RequestBody CreateFeatureFlagRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(featureFlagService.createFeatureFlag(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'READ')")
    @Operation(summary = "List all feature flags")
    public ResponseEntity<List<FeatureFlagDto>> listFeatureFlags(
            @RequestParam(required = false) Boolean enabled) {
        if (Boolean.TRUE.equals(enabled)) {
            return ResponseEntity.ok(featureFlagService.findEnabled());
        }
        return ResponseEntity.ok(featureFlagService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'READ')")
    @Operation(summary = "Get feature flag by ID")
    public ResponseEntity<FeatureFlagDto> getFeatureFlag(@PathVariable String id) {
        return featureFlagService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{key}")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'READ')")
    @Operation(summary = "Get feature flag by key")
    public ResponseEntity<FeatureFlagDto> getFeatureFlagByKey(@PathVariable String key) {
        return featureFlagService.findByKey(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'UPDATE')")
    @Operation(summary = "Update feature flag")
    public ResponseEntity<FeatureFlagDto> updateFeatureFlag(
            @PathVariable String id,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(featureFlagService.updateFeatureFlag(id, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'DELETE')")
    @Operation(summary = "Delete feature flag")
    public ResponseEntity<Void> deleteFeatureFlag(@PathVariable String id) {
        featureFlagService.deleteFeatureFlag(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{key}/evaluate")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'READ')")
    @Operation(summary = "Evaluate feature flag for context")
    public ResponseEntity<FeatureFlagEvaluationResponse> evaluateFeatureFlag(
            @PathVariable String key,
            @Valid @RequestBody FeatureFlagEvaluationRequest request) {
        return ResponseEntity.ok(featureFlagService.evaluateFlag(key, request));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasPermission('FEATURE_FLAG', 'READ')")
    @Operation(summary = "Evaluate all feature flags for context")
    public ResponseEntity<Map<String, FeatureFlagEvaluationResponse>> evaluateAllFeatureFlags(
            @Valid @RequestBody FeatureFlagEvaluationRequest request) {
        return ResponseEntity.ok(featureFlagService.evaluateAllFlags(request));
    }
}