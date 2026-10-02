package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.RoutingDecisionDto;
import com.nexusops.ticketing.dto.RoutingReorderRequest;
import com.nexusops.ticketing.dto.RoutingRuleDto;
import com.nexusops.ticketing.dto.RoutingRuleRequest;
import com.nexusops.ticketing.dto.RoutingSimulationRequest;
import com.nexusops.ticketing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Regras de roteamento e simulador (ADR-013, Fase E). Só administradores configuram. */
@RestController
@RequestMapping("/routing")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Configurable ticket routing rules and simulator")
public class RoutingController {

    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final RoutingService routingService;
    private final SecurityUtils securityUtils;

    @GetMapping("/rules")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Rules in evaluation order")
    public ResponseEntity<List<RoutingRuleDto>> list() {
        return ResponseEntity.ok(routingService.list(tenant()));
    }

    @PostMapping("/rules")
    @PreAuthorize(ADMINS)
    public ResponseEntity<RoutingRuleDto> create(@Valid @RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(routingService.create(request, currentUser(), tenant()));
    }

    @PatchMapping("/rules/{id}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Edit a rule (null keeps; conditions/actions replace as a whole)")
    public ResponseEntity<RoutingRuleDto> update(@PathVariable String id, @Valid @RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(routingService.update(id, request, currentUser(), tenant()));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(ADMINS)
    public ResponseEntity<Void> delete(@PathVariable String id) {
        routingService.delete(id, tenant());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/rules/order")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Set the evaluation order (all rule IDs, once each)")
    public ResponseEntity<List<RoutingRuleDto>> reorder(@Valid @RequestBody RoutingReorderRequest request) {
        return ResponseEntity.ok(routingService.reorder(request.ids(), tenant()));
    }

    @PostMapping("/simulate")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Where would this ticket go? Same path as a real ticket, with a trace, nothing saved")
    public ResponseEntity<RoutingDecisionDto> simulate(@RequestBody RoutingSimulationRequest request) {
        return ResponseEntity.ok(routingService.simulate(request, tenant()));
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String currentUser() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
