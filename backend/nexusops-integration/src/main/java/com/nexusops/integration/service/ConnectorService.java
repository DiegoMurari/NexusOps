package com.nexusops.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.integration.domain.Connector;
import com.nexusops.integration.dto.ConnectorDto;
import com.nexusops.integration.dto.CreateConnectorRequest;
import com.nexusops.integration.dto.UpdateConnectorRequest;
import com.nexusops.integration.repository.ConnectorRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Connector definitions (Jira, Slack, Teams...). Only non-secret settings are stored and no adapter
 * talks to the external system yet, so a connector stays DISCONNECTED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConnectorService {

    private static final Pattern SECRET_KEY =
        Pattern.compile("(?i).*(token|secret|password|passwd|credential|api[_-]?key|authorization).*");
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() { };

    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;

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
        return toDto(connectorRepository.save(connector));
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
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ValidationException("Name must not be blank");
            }
            connector.setName(request.getName().trim());
        }
        if (request.getConfiguration() != null) {
            connector.setConfiguration(toJson(request.getConfiguration()));
        }
        if (request.getSyncScheduleCron() != null) {
            connector.setSyncScheduleCron(validCron(request.getSyncScheduleCron()));
        }
        connector.setUpdatedBy(userId);
        return toDto(connectorRepository.save(connector));
    }

    public void delete(String id, String tenantId) {
        connectorRepository.delete(find(id, tenantId));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByType(String tenantId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Connector c : connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            counts.merge(c.getType().name(), 1L, Long::sum);
        }
        return counts;
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

    private ConnectorDto toDto(Connector c) {
        Map<String, String> configuration = Collections.emptyMap();
        if (c.getConfiguration() != null && !c.getConfiguration().isBlank()) {
            try {
                configuration = objectMapper.readValue(c.getConfiguration(), MAP_TYPE);
            } catch (JsonProcessingException e) {
                configuration = Collections.emptyMap();
            }
        }
        return ConnectorDto.builder()
            .id(c.getId()).name(c.getName()).type(c.getType()).configuration(configuration)
            .status(c.getStatus()).syncScheduleCron(c.getSyncScheduleCron())
            .lastSyncAt(c.getLastSyncAt()).lastSyncStatus(c.getLastSyncStatus())
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
