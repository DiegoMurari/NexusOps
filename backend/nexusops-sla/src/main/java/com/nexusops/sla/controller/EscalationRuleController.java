package com.nexusops.sla.controller;

import com.nexusops.sla.domain.EscalationRule;
import com.nexusops.sla.dto.EscalationRuleDto;
import com.nexusops.sla.service.EscalationRuleService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sla/escalation-rules")
@RequiredArgsConstructor
@Tag(name = "Escalation Rules", description = "Escalation rule management endpoints")
public class EscalationRuleController {

    private final EscalationRuleService escalationRuleService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('SLA', 'CREATE')")
    @Operation(summary = "Create a new escalation rule")
    public ResponseEntity<EscalationRuleDto> createRule(@RequestBody EscalationRule rule) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(escalationRuleService.createRule(rule, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "List escalation rules for current tenant")
    public ResponseEntity<List<EscalationRuleDto>> listRules(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String slaDefinitionId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();

        if (slaDefinitionId != null) {
            return ResponseEntity.ok(escalationRuleService.findBySlaDefinitionId(slaDefinitionId));
        }
        
        if (Boolean.TRUE.equals(active)) {
            return ResponseEntity.ok(escalationRuleService.findActiveByTenantId(tenantId));
        }
        return ResponseEntity.ok(escalationRuleService.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'READ')")
    @Operation(summary = "Get escalation rule by ID")
    public ResponseEntity<EscalationRuleDto> getRule(@PathVariable String id) {
        return escalationRuleService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'UPDATE')")
    @Operation(summary = "Update escalation rule")
    public ResponseEntity<EscalationRuleDto> updateRule(
            @PathVariable String id,
            @RequestBody EscalationRule rule) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(escalationRuleService.updateRule(id, rule, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SLA', 'DELETE')")
    @Operation(summary = "Delete escalation rule")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        escalationRuleService.deleteRule(id);
        return ResponseEntity.ok().build();
    }
}