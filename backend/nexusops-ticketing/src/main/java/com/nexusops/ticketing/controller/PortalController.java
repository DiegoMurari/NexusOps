package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.PortalDto;
import com.nexusops.ticketing.service.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Portal do Solicitante (ADR-013): a experiência própria de quem abre pedidos. Tudo é escopado ao chamador
 * ("chamados que eu abri"), qualquer que seja o papel dele, e nunca expõe fila, prioridade, SLA nem eventos internos.
 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
@Tag(name = "Portal", description = "Requester portal: my requests, requester-safe view")
@PreAuthorize("hasPermission('TICKET', 'READ')")
public class PortalController {

    private final PortalService portalService;
    private final SecurityUtils securityUtils;

    @GetMapping("/tickets")
    @Operation(summary = "My requests; the ones that need my attention come first")
    public ResponseEntity<List<PortalDto.Ticket>> list() {
        return ResponseEntity.ok(portalService.list(principal(), tenant()));
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "One of my requests, with the public history and evidence")
    public ResponseEntity<PortalDto.Detail> detail(@PathVariable String id) {
        return ResponseEntity.ok(portalService.detail(id, principal(), tenant()));
    }

    @PostMapping("/tickets")
    @Operation(summary = "Open a request: topic, title, form answers and evidence; queue, priority and SLA are derived")
    public ResponseEntity<PortalDto.Detail> create(@Valid @RequestBody PortalDto.CreateRequest request) {
        return ResponseEntity.ok(portalService.create(request, principal(), tenant()));
    }

    @PostMapping("/tickets/{id}/reply")
    @Operation(summary = "Reply to the team; answers an information request when there is one")
    public ResponseEntity<PortalDto.Detail> reply(@PathVariable String id, @Valid @RequestBody PortalDto.MessageRequest request) {
        return ResponseEntity.ok(portalService.reply(id, request.message(), principal(), tenant()));
    }

    @PostMapping("/tickets/{id}/accept")
    @Operation(summary = "Accept the proposed solution (closes the request)")
    public ResponseEntity<PortalDto.Detail> accept(@PathVariable String id, @Valid @RequestBody(required = false) PortalDto.DecisionRequest request) {
        return ResponseEntity.ok(portalService.accept(id, request == null ? null : request.comment(), principal(), tenant()));
    }

    @PostMapping("/tickets/{id}/contest")
    @Operation(summary = "Contest the proposed solution; a reason is required and a new service cycle starts")
    public ResponseEntity<PortalDto.Detail> contest(@PathVariable String id, @Valid @RequestBody PortalDto.DecisionRequest request) {
        return ResponseEntity.ok(portalService.contest(id, request.comment(), principal(), tenant()));
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String principal() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
