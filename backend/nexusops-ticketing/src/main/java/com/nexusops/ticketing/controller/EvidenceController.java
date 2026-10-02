package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.EvidenceDto;
import com.nexusops.ticketing.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Evidências ligadas ao chamado, a um comentário ou a um evento da timeline (ADR-013, Fase F). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Evidence", description = "Evidence attached to a ticket, comment or timeline event")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/evidence/staged", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Upload a file for the ticket-opening form; it counts once the ticket is created")
    public ResponseEntity<EvidenceDto> stage(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(evidenceService.stage(file, tenant(), principal()));
    }

    @PostMapping(value = "/tickets/{ticketId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Attach evidence to the ticket, one of its comments, or one of its timeline events")
    public ResponseEntity<EvidenceDto> attach(
        @PathVariable String ticketId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "subjectType", required = false) String subjectType,
        @RequestParam(value = "subjectId", required = false) String subjectId,
        @RequestParam(value = "internal", defaultValue = "false") boolean internal) {
        return ResponseEntity.ok(evidenceService.attach(ticketId, file, subjectType, subjectId, internal, tenant(), principal()));
    }

    @GetMapping("/tickets/{ticketId}/evidence")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Evidence of a ticket; the requester only sees the public ones")
    public ResponseEntity<List<EvidenceDto>> list(@PathVariable String ticketId) {
        return ResponseEntity.ok(evidenceService.list(ticketId, tenant(), principal()));
    }

    @GetMapping("/evidence/{id}/content")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Download; always as an attachment, never rendered by the browser")
    public ResponseEntity<Resource> content(@PathVariable String id) {
        EvidenceService.Content content = evidenceService.content(id, tenant(), principal());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(content.attachment().getFileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(content.attachment().getMimeType()))
            .contentLength(content.attachment().getFileSize())
            .body(content.resource());
    }

    @DeleteMapping("/evidence/{id}")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Remove evidence (who sent it, or staff); the timeline keeps the record")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        evidenceService.remove(id, tenant(), principal());
        return ResponseEntity.noContent().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String principal() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
