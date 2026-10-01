package com.nexusops.ticketing.controller;

import com.nexusops.ticketing.dto.TimeEntryDto;
import com.nexusops.ticketing.dto.CreateTimeEntryRequest;
import com.nexusops.ticketing.service.TimeTrackingService;
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
@RequestMapping("/tickets/{ticketId}/time-entries")
@RequiredArgsConstructor
@Tag(name = "Time Tracking", description = "Time tracking endpoints")
public class TimeTrackingController {

    private final TimeTrackingService timeTrackingService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Add time entry to ticket")
    public ResponseEntity<TimeEntryDto> addTimeEntry(
            @PathVariable String ticketId,
            @Valid @RequestBody CreateTimeEntryRequest request) {
        return ResponseEntity.ok(timeTrackingService.addTimeEntry(ticketId, request));
    }

    @GetMapping
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List time entries for ticket")
    public ResponseEntity<List<TimeEntryDto>> listTimeEntries(@PathVariable String ticketId) {
        return ResponseEntity.ok(timeTrackingService.getTimeEntriesByTicketId(ticketId));
    }

    @GetMapping("/total")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get total time spent on ticket")
    public ResponseEntity<Integer> getTotalTime(@PathVariable String ticketId) {
        Integer total = timeTrackingService.getTotalDurationByTicketId(ticketId);
        return ResponseEntity.ok(total != null ? total : 0);
    }

    @GetMapping("/my-time")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get time entries for current user")
    public ResponseEntity<List<TimeEntryDto>> getMyTimeEntries() {
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(timeTrackingService.getTimeEntriesByUserId(userId));
    }

    @DeleteMapping("/{entryId}")
    @PreAuthorize("hasPermission('TICKET', 'DELETE')")
    @Operation(summary = "Delete time entry")
    public ResponseEntity<Void> deleteTimeEntry(
            @PathVariable String ticketId,
            @PathVariable String entryId) {
        timeTrackingService.deleteTimeEntry(entryId);
        return ResponseEntity.ok().build();
    }
}