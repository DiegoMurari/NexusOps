package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.QueueDto;
import com.nexusops.ticketing.dto.QueueMemberDto;
import com.nexusops.ticketing.dto.QueueMemberRequest;
import com.nexusops.ticketing.dto.QueueRequest;
import com.nexusops.ticketing.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/queues")
@RequiredArgsConstructor
@Tag(name = "Queues", description = "Operational queues and their members")
public class QueueController {

    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','ADMIN')";
    private static final String STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER','TEAM_LEAD','AGENT')";

    private final QueueService queueService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize(STAFF)
    @Operation(summary = "List queues of the current tenant")
    public ResponseEntity<List<QueueDto>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(queueService.list(tenant(), includeInactive));
    }

    @GetMapping("/mine")
    @PreAuthorize(STAFF)
    @Operation(summary = "Queues the current user belongs to")
    public ResponseEntity<List<QueueDto>> mine() {
        // O principal é o e-mail; os membros guardam o ID. O serviço resolve pelo diretório de usuários.
        return ResponseEntity.ok(queueService.mineByEmail(currentUser(), tenant()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    public ResponseEntity<QueueDto> get(@PathVariable String id) {
        return ResponseEntity.ok(queueService.get(id, tenant()));
    }

    @PostMapping
    @PreAuthorize(ADMINS)
    @Operation(summary = "Create a queue")
    public ResponseEntity<QueueDto> create(@Valid @RequestBody QueueRequest request) {
        return ResponseEntity.ok(queueService.create(request, currentUser(), tenant()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Edit a queue (null keeps the value)")
    public ResponseEntity<QueueDto> update(@PathVariable String id, @Valid @RequestBody QueueRequest request) {
        return ResponseEntity.ok(queueService.update(id, request, currentUser(), tenant()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Delete an unused queue")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        queueService.delete(id, tenant());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize(STAFF)
    public ResponseEntity<List<QueueMemberDto>> members(@PathVariable String id) {
        return ResponseEntity.ok(queueService.members(id, tenant()));
    }

    @PutMapping("/{id}/members")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Add a member or change their role")
    public ResponseEntity<QueueMemberDto> putMember(@PathVariable String id, @Valid @RequestBody QueueMemberRequest request) {
        return ResponseEntity.ok(queueService.putMember(id, request, tenant()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize(ADMINS)
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable String userId) {
        queueService.removeMember(id, userId, tenant());
        return ResponseEntity.noContent().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String currentUser() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
