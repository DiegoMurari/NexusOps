package com.nexusops.integration.dto;

import com.nexusops.integration.domain.IntegrationLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Resultado de um teste de webhook ou de uma verificação de conector. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationTestResultDto {
    private IntegrationLog.Outcome outcome;
    private Integer httpStatus;
    private long durationMs;
    /** Texto curto e seguro (ex.: "HTTP 204", "TIMEOUT"). */
    private String message;
    private Instant checkedAt;
}
