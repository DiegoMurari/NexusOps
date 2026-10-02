package com.nexusops.integration.dto;

import com.nexusops.integration.domain.IntegrationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationLogDto {
    private String id;
    private IntegrationLog.Kind integrationKind;
    private String integrationId;
    private String integrationName;
    private IntegrationLog.Event event;
    private IntegrationLog.Outcome outcome;
    private Integer httpStatus;
    private Long durationMs;
    private String message;
    private String actor;
    private Instant createdAt;
}
