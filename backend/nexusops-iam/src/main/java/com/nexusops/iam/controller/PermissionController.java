package com.nexusops.iam.controller;

import com.nexusops.iam.dto.PermissionDto;
import com.nexusops.iam.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission lookup endpoints")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    @Operation(summary = "List all permissions")
    public ResponseEntity<List<PermissionDto>> listPermissions() {
        return ResponseEntity.ok(permissionService.findAll());
    }

    @GetMapping("/resource/{resource}")
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    @Operation(summary = "List permissions by resource")
    public ResponseEntity<List<PermissionDto>> listByResource(@PathVariable String resource) {
        return ResponseEntity.ok(permissionService.findByResource(resource));
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    @Operation(summary = "List permissions by category")
    public ResponseEntity<List<PermissionDto>> listByCategory(@PathVariable String category) {
        return ResponseEntity.ok(permissionService.findByCategory(category));
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    @Operation(summary = "Get permission by key")
    public ResponseEntity<PermissionDto> getPermission(@PathVariable String key) {
        return permissionService.findByKey(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    @Operation(summary = "Lookup permission by resource, action, scope")
    public ResponseEntity<PermissionDto> lookupPermission(
            @RequestParam String resource,
            @RequestParam String action,
            @RequestParam String scope) {
        return permissionService.findByResourceActionScope(resource, action, scope)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}