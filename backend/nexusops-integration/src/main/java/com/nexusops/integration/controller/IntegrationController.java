package com.nexusops.integration.controller;

import com.nexusops.integration.domain.Connector;
import com.nexusops.integration.dto.ConnectorDto;
import com.nexusops.integration.dto.CreateConnectorRequest;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.IntegrationOverviewDto;
import com.nexusops.integration.dto.UpdateConnectorRequest;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.dto.WebhookDto;
import com.nexusops.integration.service.ConnectorService;
import com.nexusops.integration.service.WebhookService;
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
@RequestMapping("/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Webhook and connector configuration")
public class IntegrationController {

    private final WebhookService webhookService;
    private final ConnectorService connectorService;
    private final SecurityUtils securityUtils;

    @GetMapping("/overview")
    @PreAuthorize("hasPermission('INTEGRATION', 'READ')")
    @Operation(summary = "Integration counters")
    public ResponseEntity<IntegrationOverviewDto> overview() {
        String tenant = tenant();
        var byType = connectorService.countByType(tenant);
        return ResponseEntity.ok(IntegrationOverviewDto.builder()
            .webhooks(webhookService.count(tenant))
            .activeWebhooks(webhookService.countActive(tenant))
            .connectors(byType.values().stream().mapToLong(Long::longValue).sum())
            .connectorsByType(byType)
            .deliveryAvailable(false)
            .build());
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasPermission('INTEGRATION', 'CREATE')")
    @Operation(summary = "Create a webhook")
    public ResponseEntity<WebhookDto> createWebhook(@Valid @RequestBody CreateWebhookRequest request) {
        return ResponseEntity.ok(webhookService.create(request, tenant(), user()));
    }

    @GetMapping("/webhooks")
    @PreAuthorize("hasPermission('INTEGRATION', 'READ')")
    @Operation(summary = "List webhooks")
    public ResponseEntity<List<WebhookDto>> listWebhooks() {
        return ResponseEntity.ok(webhookService.list(tenant()));
    }

    @PatchMapping("/webhooks/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'UPDATE')")
    @Operation(summary = "Update a webhook")
    public ResponseEntity<WebhookDto> updateWebhook(@PathVariable String id,
                                                    @Valid @RequestBody UpdateWebhookRequest request) {
        return ResponseEntity.ok(webhookService.update(id, request, tenant(), user()));
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'DELETE')")
    @Operation(summary = "Delete a webhook")
    public ResponseEntity<Void> deleteWebhook(@PathVariable String id) {
        webhookService.delete(id, tenant());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/connectors")
    @PreAuthorize("hasPermission('INTEGRATION', 'CREATE')")
    @Operation(summary = "Create a connector")
    public ResponseEntity<ConnectorDto> createConnector(@Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.ok(connectorService.create(request, tenant(), user()));
    }

    @GetMapping("/connectors")
    @PreAuthorize("hasPermission('INTEGRATION', 'READ')")
    @Operation(summary = "List connectors, optionally by type")
    public ResponseEntity<List<ConnectorDto>> listConnectors(@RequestParam(required = false) Connector.ConnectorType type) {
        return ResponseEntity.ok(connectorService.list(tenant(), type));
    }

    @PatchMapping("/connectors/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'UPDATE')")
    @Operation(summary = "Update a connector")
    public ResponseEntity<ConnectorDto> updateConnector(@PathVariable String id,
                                                        @Valid @RequestBody UpdateConnectorRequest request) {
        return ResponseEntity.ok(connectorService.update(id, request, tenant(), user()));
    }

    @DeleteMapping("/connectors/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'DELETE')")
    @Operation(summary = "Delete a connector")
    public ResponseEntity<Void> deleteConnector(@PathVariable String id) {
        connectorService.delete(id, tenant());
        return ResponseEntity.ok().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String user() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
