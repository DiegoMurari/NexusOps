package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.FormDefinitionDto;
import com.nexusops.ticketing.dto.TicketFormDto;
import com.nexusops.ticketing.dto.TopicFormVersionDto;
import com.nexusops.ticketing.service.FormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Formulários dinâmicos versionados por tópico (ADR-013, Fase D). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Topic forms", description = "Versioned dynamic forms of catalog topics")
public class FormController {

    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final FormService formService;
    private final SecurityUtils securityUtils;

    @GetMapping("/catalog/topics/{topicId}/forms")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Versions of the topic form (newest first)")
    public ResponseEntity<List<TopicFormVersionDto>> versions(@PathVariable String topicId) {
        return ResponseEntity.ok(formService.list(topicId, tenant()));
    }

    @PostMapping("/catalog/topics/{topicId}/forms")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Open a draft (from the body, the published version, or empty)")
    public ResponseEntity<TopicFormVersionDto> createDraft(@PathVariable String topicId,
                                                           @RequestBody(required = false) FormDefinitionDto initial) {
        return ResponseEntity.ok(formService.createDraft(topicId, initial, currentUser(), tenant()));
    }

    @PutMapping("/catalog/forms/{versionId}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Replace the definition of a draft")
    public ResponseEntity<TopicFormVersionDto> saveDraft(@PathVariable String versionId,
                                                         @RequestBody FormDefinitionDto definition) {
        return ResponseEntity.ok(formService.saveDraft(versionId, definition, currentUser(), tenant()));
    }

    @PostMapping("/catalog/forms/{versionId}/publish")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Publish a draft; the previous published version is archived")
    public ResponseEntity<TopicFormVersionDto> publish(@PathVariable String versionId) {
        return ResponseEntity.ok(formService.publish(versionId, currentUser(), tenant()));
    }

    @DeleteMapping("/catalog/forms/{versionId}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Discard a draft")
    public ResponseEntity<Void> discard(@PathVariable String versionId) {
        formService.discardDraft(versionId, tenant());
        return ResponseEntity.noContent().build();
    }

    /** O formulário que o Portal desenha: a versão publicada do tópico (vazio se não houver). */
    @GetMapping("/catalog/portal/topics/{topicId}/form")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Published form of an active topic, for the requester Portal")
    public ResponseEntity<FormDefinitionDto> portalForm(@PathVariable String topicId) {
        return ResponseEntity.ok(formService.portalForm(topicId, tenant()));
    }

    @GetMapping("/tickets/{ticketId}/form")
    @PreAuthorize(StaffAccess.READ)
    @Operation(summary = "Form version and answers a ticket was opened with (204 when it has none)")
    public ResponseEntity<TicketFormDto> ticketForm(@PathVariable String ticketId) {
        return formService.ticketForm(ticketId, tenant())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String currentUser() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
