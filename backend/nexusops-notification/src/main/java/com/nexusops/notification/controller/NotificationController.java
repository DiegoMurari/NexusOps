package com.nexusops.notification.controller;

import com.nexusops.notification.dto.NotificationDto;
import com.nexusops.notification.service.NotificationServiceImpl;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification endpoints")
public class NotificationController {

    private final NotificationServiceImpl notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasPermission('NOTIFICATION', 'READ')")
    @Operation(summary = "List my notifications")
    public ResponseEntity<Page<NotificationDto>> listMyNotifications(@PageableDefault(size = 20) Pageable pageable) {
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(notificationService.listForUser(userId, tenantId, pageable));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasPermission('NOTIFICATION', 'UPDATE')")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationDto> markRead(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(notificationService.markRead(id, userId));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasPermission('NOTIFICATION', 'UPDATE')")
    @Operation(summary = "Mark all my notifications as read")
    public ResponseEntity<Void> markAllRead() {
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        notificationService.markAllRead(userId, tenantId);
        return ResponseEntity.ok().build();
    }
}
