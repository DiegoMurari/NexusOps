package com.nexusops.ticketing.controller;

import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.*;
import com.nexusops.ticketing.service.ConsoleService;
import com.nexusops.ticketing.service.TicketCycleService;
import com.nexusops.ticketing.service.TicketService;
import com.nexusops.ticketing.service.TicketTimelineService;
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
    private final TicketTimelineService timelineService;
    private final TicketCycleService cycleService;
    private final ConsoleService consoleService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('TICKET', 'CREATE')")
    @Operation(summary = "Create a new ticket")
    public ResponseEntity<TicketDto> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.createTicket(request, createdBy));
    }

    @GetMapping
    @PreAuthorize(StaffAccess.READ)
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

    @GetMapping("/queue-view")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "The analyst's list: scope MINE, MY_QUEUES, UNASSIGNED or ALL, optional queue, status and text")
    public ResponseEntity<Page<TicketDto>> queueView(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String queueId,
            @RequestParam(required = false) Ticket.TicketStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(consoleService.queueView(
            securityUtils.getCurrentTenantId().orElseThrow(), securityUtils.getCurrentUserId().orElseThrow(),
            ConsoleService.parseScope(scope), queueId, status, q, pageable));
    }

    @GetMapping("/counts")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Open tickets per scope: mine, my queues, unassigned, all")
    public ResponseEntity<ConsoleDto.Counts> counts() {
        return ResponseEntity.ok(consoleService.counts(
            securityUtils.getCurrentTenantId().orElseThrow(), securityUtils.getCurrentUserId().orElseThrow()));
    }

    @GetMapping("/{id}/context")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Queue, topic, area, people and form answers of a ticket, names already resolved")
    public ResponseEntity<ConsoleDto.Context> context(@PathVariable String id) {
        return ResponseEntity.ok(consoleService.context(id, securityUtils.getCurrentTenantId().orElseThrow()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Get ticket by ID")
    public ResponseEntity<TicketDto> getTicket(@PathVariable String id) {
        return ticketService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{ticketNumber}")
    @PreAuthorize(StaffAccess.READ)
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

    @GetMapping("/{id}/timeline")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Append-only timeline of a ticket; internal events (routing, queues, SLA) only for staff")
    public ResponseEntity<List<TicketEventDto>> getTimeline(@PathVariable String id) {
        // O solicitante não vê a estrutura interna de filas, roteamento e SLA (ADR-013).
        boolean staff = SecurityUtils.hasAnyRole("SUPER_ADMIN", "ADMIN", "MANAGER", "TEAM_LEAD", "AGENT");
        return ResponseEntity.ok(timelineService.timeline(id, staff));
    }

    @GetMapping("/{id}/cycles")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Service cycles of a ticket with the solution and outcome of each cycle")
    public ResponseEntity<TicketCyclesDto> getCycles(@PathVariable String id) {
        return ResponseEntity.ok(cycleService.cycles(id));
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

    @PostMapping("/{id}/queue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER','TEAM_LEAD','AGENT')")
    @Operation(summary = "Move ticket to another queue (assignee unchanged)")
    public ResponseEntity<TicketDto> changeQueue(
            @PathVariable String id,
            @Valid @RequestBody com.nexusops.ticketing.dto.ChangeQueueRequest request) {
        String changedBy = securityUtils.getCurrentUserId().orElseThrow();
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(ticketService.changeQueue(id, request, changedBy, tenantId));
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
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Get tickets assigned to current user")
    public ResponseEntity<List<TicketDto>> getMyTickets() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(ticketService.findAssignedToUser(tenantId, userId));
    }

    @GetMapping("/stats")
    @PreAuthorize(StaffAccess.READ)
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