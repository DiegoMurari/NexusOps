package com.nexusops.iam.controller;

import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateProfileRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.dto.UserResponse;
import com.nexusops.iam.security.CallerPermissions;
import com.nexusops.iam.service.UserService;
import com.nexusops.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasPermission('USER', 'CREATE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(
            request, actor(), tenant(), CallerPermissions.current(), CallerPermissions.currentRoles()));
    }

    /** The caller's own profile. Authenticated only: requesters hold no USER permission. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.getProfile(actor(), tenant()));
    }

    /** Self-service profile edit (names, contact, job data, default location). Never roles or status. */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(actor(), request, tenant()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('USER', 'READ')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id, tenant()));
    }

    /** Lists the caller's own tenant only; a tenantId parameter is accepted for compatibility but ignored. */
    @GetMapping
    @PreAuthorize("hasPermission('USER', 'READ')")
    public ResponseEntity<List<UserResponse>> getUsers(@RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(userService.getUsersByTenant(tenant()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('USER', 'UPDATE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(
            id, request, actor(), tenant(), CallerPermissions.current(), CallerPermissions.currentRoles()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('USER', 'DELETE')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id, tenant(), actor(), CallerPermissions.currentRoles());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/change-password")
    @PreAuthorize("hasPermission('USER', 'UPDATE')")
    public ResponseEntity<UserResponse> changePassword(
            @PathVariable UUID id,
            @RequestParam String currentPassword,
            @RequestParam String newPassword) {
        return ResponseEntity.ok(userService.changePassword(id, currentPassword, newPassword, tenant()));
    }

    private static String tenant() {
        return SecurityUtils.getCurrentTenantId().orElseThrow();
    }

    private static String actor() {
        return SecurityUtils.getCurrentUserId().orElse("system");
    }
}
