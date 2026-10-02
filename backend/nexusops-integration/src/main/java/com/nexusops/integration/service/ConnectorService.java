package com.nexusops.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.Connector;
import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.dto.ConnectorDto;
import com.nexusops.integration.dto.CreateConnectorRequest;
import com.nexusops.integration.dto.IntegrationTestResultDto;
import com.nexusops.integration.dto.UpdateConnectorRequest;
import com.nexusops.integration.repository.ConnectorRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Definições de conector (Jira, Slack, Teams...), liga/desliga, verificação de alcance e histórico. Só
 * configuração não secreta é guardada; nenhum adaptador sincroniza com o sistema externo ainda. A verificação
 * apenas confirma que o endereço configurado ({@code baseUrl}) responde, sem credenciais.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConnectorService {

    private static final Pattern SECRET_KEY =
        Pattern.compile("(?i).*(token|secret|password|passwd|credential|api[_-]?key|authorization).*");
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() { };
    static final String BASE_URL_KEY = "baseUrl";
    private static final int CHECK_TIMEOUT_SECONDS = 10;

    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;
    private final IntegrationLogService logService;
    private final SafeHttpSender sender;

    public ConnectorDto create(CreateConnectorRequest request, String tenantId, String userId) {
        Connector connector = Connector.builder()
            .tenantId(tenantId)
            .name(request.getName().trim())
            .type(request.getType())
            .configuration(toJson(request.getConfiguration()))
            .syncScheduleCron(validCron(request.getSyncScheduleCron()))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        Connector saved = connectorRepository.save(connector);
        logService.recordChange(tenantId, IntegrationLog.Kind.CONNECTOR, saved.getId(), saved.getName(),
            IntegrationLog.Event.CREATED, userId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ConnectorDto> list(String tenantId, Connector.ConnectorType type) {
        List<Connector> connectors = type == null
            ? connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            : connectorRepository.findByTenantIdAndTypeOrderByCreatedAtDesc(tenantId, type);
        return connectors.stream().map(this::toDto).toList();
    }

    public ConnectorDto update(String id, UpdateConnectorRequest request, String tenantId, String userId) {
        Connector connector = find(id, tenantId);
        boolean configChanged = false;
        IntegrationLog.Event toggleEvent = null;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ValidationException("Name must not be blank");
            }
            connector.setName(request.getName().trim());
            configChanged = true;
        }
        if (request.getConfiguration() != null) {
            connector.setConfiguration(toJson(request.getConfiguration()));
            configChanged = true;
        }
        if (request.getSyncScheduleCron() != null) {
            connector.setSyncScheduleCron(validCron(request.getSyncScheduleCron()));
            configChanged = true;
        }
        if (request.getEnabled() != null && request.getEnabled() != connector.isEnabled()) {
            connector.setEnabled(request.getEnabled());
            toggleEvent = request.getEnabled() ? IntegrationLog.Event.ENABLED : IntegrationLog.Event.DISABLED;
        }
        connector.setUpdatedBy(userId);
        Connector saved = connectorRepository.save(connector);

        if (configChanged) {
            logService.recordChange(tenantId, IntegrationLog.Kind.CONNECTOR, saved.getId(), saved.getName(),
                IntegrationLog.Event.UPDATED, userId);
        }
        if (toggleEvent != null) {
            logService.recordChange(tenantId, IntegrationLog.Kind.CONNECTOR, saved.getId(), saved.getName(),
                toggleEvent, userId);
        }
        return toDto(saved);
    }

    public void delete(String id, String tenantId, String userId) {
        Connector connector = find(id, tenantId);
        logService.recordChange(tenantId, IntegrationLog.Kind.CONNECTOR, connector.getId(), connector.getName(),
            IntegrationLog.Event.DELETED, userId);
        connectorRepository.delete(connector);
    }

    /**
     * Verifica se o endereço configurado responde. Não envia credenciais e não diz que o conector está
     * "conectado": só que o endereço está de pé. Roda fora de transação por causa da chamada de rede.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IntegrationTestResultDto check(String id, String tenantId, String userId) {
        Connector connector = find(id, tenantId);
        if (!connector.isEnabled()) {
            throw new ValidationException("Connector is disabled");
        }
        String baseUrl = readConfiguration(connector).get(BASE_URL_KEY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ValidationException("This connector has no base URL to check");
        }

        SafeHttpSender.Result result = sender.get(baseUrl.trim(), CHECK_TIMEOUT_SECONDS);
        boolean ok = result.reachable();
        IntegrationLog.Outcome outcome = ok ? IntegrationLog.Outcome.SUCCESS : IntegrationLog.Outcome.FAILURE;
        Instant checkedAt = Instant.now();

        connectorRepository.recordCheck(connector.getId(), tenantId, checkedAt, outcome.name(), result.summary());
        logService.record(tenantId, IntegrationLog.Kind.CONNECTOR, connector.getId(), connector.getName(),
            IntegrationLog.Event.CHECK, outcome, result.httpStatus(), result.durationMs(), result.summary(), userId);

        return IntegrationTestResultDto.builder()
            .outcome(outcome).httpStatus(result.httpStatus()).durationMs(result.durationMs())
            .message(result.summary()).checkedAt(checkedAt)
            .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByType(String tenantId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Connector c : connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            counts.merge(c.getType().name(), 1L, Long::sum);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public long countDisabled(String tenantId) {
        return connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(c -> !c.isEnabled()).count();
    }

    @Transactional(readOnly = true)
    public long countFailing(String tenantId) {
        return connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(c -> "FAILURE".equals(c.getLastCheckStatus())).count();
    }

    private Connector find(String id, String tenantId) {
        return connectorRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Connector", id));
    }

    private String toJson(Map<String, String> configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return null;
        }
        for (String key : configuration.keySet()) {
            if (key == null || key.isBlank() || SECRET_KEY.matcher(key).matches()) {
                throw new ValidationException("Credentials cannot be stored in connector settings: " + key);
            }
        }
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid connector configuration");
        }
    }

    private Map<String, String> readConfiguration(Connector c) {
        if (c.getConfiguration() == null || c.getConfiguration().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(c.getConfiguration(), MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private ConnectorDto toDto(Connector c) {
        return ConnectorDto.builder()
            .id(c.getId()).name(c.getName()).type(c.getType()).configuration(readConfiguration(c))
            .status(c.getStatus()).enabled(c.isEnabled()).syncScheduleCron(c.getSyncScheduleCron())
            .lastSyncAt(c.getLastSyncAt()).lastSyncStatus(c.getLastSyncStatus())
            .lastCheckAt(c.getLastCheckAt()).lastCheckStatus(c.getLastCheckStatus())
            .lastCheckMessage(c.getLastCheckMessage())
            .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
            .build();
    }

    private static String validCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid cron expression (expected 6 fields: sec min hour day month weekday)");
        }
        return cron.trim();
    }
}
