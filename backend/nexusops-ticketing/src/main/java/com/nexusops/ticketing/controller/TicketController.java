package com.nexusops.ticketing.controller;

import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.*;
import com.nexusops.ticketing.service.TicketService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Ticket management endpoints")
public class TicketController {

    private final TicketService ticketService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('TICKET', 'CREATE')")
    @Operation(summary = "Create a new ticket")
    public ResponseEntity<TicketDto> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.createTicket(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List tickets with pagination")
    public ResponseEntity<Page<TicketDto>> listTickets(
            @RequestParam(required = false) Ticket.TicketStatus status,
            @RequestParam(required = false) String assigneeId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();

        if (assigneeId != null && "me".equals(assigneeId)) {
            assigneeId = securityUtils.getCurrentUserId().orElseThrow();
        }

        if (assigneeId != null) {
            return ResponseEntity.ok(ticketService.findByTenantId(tenantId, pageable));
        }
        
        if (status != null) {
            List<TicketDto> tickets = ticketService.findByTenantIdAndStatus(tenantId, status);
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(tickets, pageable, tickets.size()));
        }

        return ResponseEntity.ok(ticketService.findByTenantId(tenantId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get ticket by ID")
    public ResponseEntity<TicketDto> getTicket(@PathVariable String id) {
        return ticketService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{ticketNumber}")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get ticket by number")
    public ResponseEntity<TicketDto> getTicketByNumber(@PathVariable String ticketNumber) {
        return ticketService.findByTicketNumber(ticketNumber)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Update ticket")
    public ResponseEntity<TicketDto> updateTicket(
            @PathVariable String id,
            @Valid @RequestBody UpdateTicketRequest request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.updateTicket(id, request, updatedBy));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Transition ticket status")
    public ResponseEntity<TicketDto> transitionTicket(
            @PathVariable String id,
            @Valid @RequestBody TransitionRequest request) {
        String changedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.transitionTicket(id, request, changedBy));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Assign ticket to user")
    public ResponseEntity<TicketDto> assignTicket(
            @PathVariable String id,
            @Valid @RequestBody AssignRequest request) {
        String assignedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.assignTicket(id, request, assignedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'DELETE')")
    @Operation(summary = "Delete ticket")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-tickets")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get tickets assigned to current user")
    public ResponseEntity<List<TicketDto>> getMyTickets() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.findAssignedToUser(tenantId, userId));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get ticket statistics")
    public ResponseEntity<TicketStatsDto> getStats() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        Ticket.TicketStatus[] statuses = Ticket.TicketStatus.values();
        
        TicketStatsDto stats = new TicketStatsDto();
        for (Ticket.TicketStatus status : statuses) {
            long count = ticketService.countByTenantIdAndStatus(tenantId, status);
            stats.addCount(status.name(), count);
        }
        stats.setAssignedToMe(ticketService.countAssignedToUser(tenantId, securityUtils.getCurrentUserId().orElseThrow()));
        
        return ResponseEntity.ok(stats);
    }
}