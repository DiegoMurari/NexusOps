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
    private String syncScheduleCron;
    private Instant lastSyncAt;
    private String lastSyncStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
