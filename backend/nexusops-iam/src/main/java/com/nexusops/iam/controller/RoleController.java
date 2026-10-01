package com.nexusops.iam.controller;

import com.nexusops.iam.dto.CreateRoleRequest;
import com.nexusops.iam.dto.UpdateRoleRequest;
import com.nexusops.iam.dto.RoleResponse;
import com.nexusops.iam.security.CallerPermissions;
import com.nexusops.iam.service.RoleService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role administration (tenant scoped, system roles are read-only)")
public class RoleController {

    private final RoleService roleService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasPermission('ROLE', 'READ')")
    @Operation(summary = "List system roles and the tenant's custom roles")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles(tenant()));
    }

    @PostMapping
    @PreAuthorize("hasPermission('ROLE', 'CREATE')")
    @Operation(summary = "Create a custom role")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(request, tenant(), callerPermissions()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'READ')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getRoleById(id, tenant()));
    }

    @GetMapping("/name/{name}")
    @PreAuthorize("hasPermission('ROLE', 'READ')")
    public ResponseEntity<RoleResponse> getRoleByName(@PathVariable String name) {
        return ResponseEntity.ok(roleService.getRoleByName(name, tenant()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'UPDATE')")
    @Operation(summary = "Update a custom role's description and permissions")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request, tenant(), callerPermissions()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id, tenant());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasPermission('ROLE', 'UPDATE')")
    public ResponseEntity<RoleResponse> assignPermission(@PathVariable UUID id, @RequestParam String permission) {
        return ResponseEntity.ok(roleService.assignPermission(id, permission, tenant(), callerPermissions()));
    }

    @DeleteMapping("/{id}/permissions")
    @PreAuthorize("hasPermission('ROLE', 'UPDATE')")
    public ResponseEntity<RoleResponse> removePermission(@PathVariable UUID id, @RequestParam String permission) {
        return ResponseEntity.ok(roleService.removePermission(id, permission, tenant()));
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    /** The permissions the caller holds, the ceiling for what they may grant. */
    private static Set<String> callerPermissions() {
        return CallerPermissions.current();
    }
}
