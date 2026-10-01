package com.nexusops.platform.controller;

import com.nexusops.platform.dto.*;
import com.nexusops.platform.service.SystemSettingService;
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
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "System settings management endpoints")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('SETTING', 'CREATE')")
    @Operation(summary = "Create a new system setting")
    public ResponseEntity<SystemSettingDto> createSetting(@Valid @RequestBody CreateSystemSettingRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(systemSettingService.createSetting(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SETTING', 'READ')")
    @Operation(summary = "List all system settings")
    public ResponseEntity<List<SystemSettingDto>> listSettings(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isPublic) {
        if (category != null) {
            return ResponseEntity.ok(systemSettingService.findByCategory(category));
        }
        if (Boolean.TRUE.equals(isPublic)) {
            return ResponseEntity.ok(systemSettingService.findPublicSettings());
        }
        return ResponseEntity.ok(systemSettingService.findAll());
    }

    @GetMapping("/public")
    @Operation(summary = "List public settings (no auth required)")
    public ResponseEntity<List<SystemSettingDto>> listPublicSettings() {
        return ResponseEntity.ok(systemSettingService.findPublicSettings());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SETTING', 'READ')")
    @Operation(summary = "Get system setting by ID")
    public ResponseEntity<SystemSettingDto> getSetting(@PathVariable String id) {
        return systemSettingService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{key}")
    @PreAuthorize("hasPermission('SETTING', 'READ')")
    @Operation(summary = "Get system setting by key")
    public ResponseEntity<SystemSettingDto> getSettingByKey(@PathVariable String key) {
        return systemSettingService.findByKey(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('SETTING', 'UPDATE')")
    @Operation(summary = "Update system setting")
    public ResponseEntity<SystemSettingDto> updateSetting(
            @PathVariable String id,
            @Valid @RequestBody UpdateSystemSettingRequest request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(systemSettingService.updateSetting(id, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SETTING', 'DELETE')")
    @Operation(summary = "Delete system setting")
    public ResponseEntity<Void> deleteSetting(@PathVariable String id) {
        systemSettingService.deleteSetting(id);
        return ResponseEntity.ok().build();
    }
}