package com.nexusops.integration.controller;

import com.nexusops.integration.domain.Connector;
import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.dto.ConnectorDto;
import com.nexusops.integration.dto.CreateConnectorRequest;
import com.nexusops.integration.dto.CreateWebhookRequest;
import com.nexusops.integration.dto.IntegrationLogDto;
import com.nexusops.integration.dto.IntegrationOverviewDto;
import com.nexusops.integration.dto.IntegrationTestResultDto;
import com.nexusops.integration.dto.UpdateConnectorRequest;
import com.nexusops.integration.dto.UpdateWebhookRequest;
import com.nexusops.integration.dto.WebhookDto;
import com.nexusops.integration.service.ConnectorService;
import com.nexusops.integration.service.IntegrationLogService;
import com.nexusops.integration.service.WebhookService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Webhook and connector configuration, health and activity log")
public class IntegrationController {

    private final WebhookService webhookService;
    private final ConnectorService connectorService;
    private final IntegrationLogService logService;
    private final SecurityUtils securityUtils;

    @GetMapping("/overview")
    @PreAuthorize("hasPermission('INTEGRATION', 'READ')")
    @Operation(summary = "Integration counters and health")
    public ResponseEntity<IntegrationOverviewDto> overview() {
        String tenant = tenant();
        var byType = connectorService.countByType(tenant);
        return ResponseEntity.ok(IntegrationOverviewDto.builder()
            .webhooks(webhookService.count(tenant))
            .activeWebhooks(webhookService.countActive(tenant))
            .failingWebhooks(webhookService.countFailing(tenant))
            .connectors(byType.values().stream().mapToLong(Long::longValue).sum())
            .disabledConnectors(connectorService.countDisabled(tenant))
            .failingConnectors(connectorService.countFailing(tenant))
            .connectorsByType(byType)
            .deliveryAvailable(true)
            .build());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasPermission('INTEGRATION', 'READ')")
    @Operation(summary = "Activity log of integrations, newest first")
    public ResponseEntity<Page<IntegrationLogDto>> logs(
            @RequestParam(required = false) IntegrationLog.Kind kind,
            @RequestParam(required = false) String integrationId,
            @RequestParam(required = false) IntegrationLog.Outcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(logService.list(tenant(), kind, integrationId, outcome, page, size));
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
    @Operation(summary = "Update or enable/disable a webhook")
    public ResponseEntity<WebhookDto> updateWebhook(@PathVariable String id,
                                                    @Valid @RequestBody UpdateWebhookRequest request) {
        return ResponseEntity.ok(webhookService.update(id, request, tenant(), user()));
    }

    @PostMapping("/webhooks/{id}/test")
    @PreAuthorize("hasPermission('INTEGRATION', 'UPDATE')")
    @Operation(summary = "Send a signed ping event to the webhook and record the result")
    public ResponseEntity<IntegrationTestResultDto> testWebhook(@PathVariable String id) {
        return ResponseEntity.ok(webhookService.sendTest(id, tenant(), user()));
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'DELETE')")
    @Operation(summary = "Delete a webhook")
    public ResponseEntity<Void> deleteWebhook(@PathVariable String id) {
        webhookService.delete(id, tenant(), user());
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
    @Operation(summary = "Update or enable/disable a connector")
    public ResponseEntity<ConnectorDto> updateConnector(@PathVariable String id,
                                                        @Valid @RequestBody UpdateConnectorRequest request) {
        return ResponseEntity.ok(connectorService.update(id, request, tenant(), user()));
    }

    @PostMapping("/connectors/{id}/check")
    @PreAuthorize("hasPermission('INTEGRATION', 'UPDATE')")
    @Operation(summary = "Check that the connector's base URL responds and record the result")
    public ResponseEntity<IntegrationTestResultDto> checkConnector(@PathVariable String id) {
        return ResponseEntity.ok(connectorService.check(id, tenant(), user()));
    }

    @DeleteMapping("/connectors/{id}")
    @PreAuthorize("hasPermission('INTEGRATION', 'DELETE')")
    @Operation(summary = "Delete a connector")
    public ResponseEntity<Void> deleteConnector(@PathVariable String id) {
        connectorService.delete(id, tenant(), user());
        return ResponseEntity.ok().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String user() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
