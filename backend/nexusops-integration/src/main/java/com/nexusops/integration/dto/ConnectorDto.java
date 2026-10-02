package com.nexusops.integration.dto;

import com.nexusops.integration.domain.Connector;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorDto {

    private String id;
    private String name;
    private Connector.ConnectorType type;
    private Map<String, String> configuration;
    private Connector.ConnectorStatus status;
    private boolean enabled;
    private String syncScheduleCron;
    private Instant lastSyncAt;
    private String lastSyncStatus;
    /** Última verificação de alcance do endereço configurado (SUCCESS ou FAILURE; nulo se nunca feita). */
    private Instant lastCheckAt;
    private String lastCheckStatus;
    private String lastCheckMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
