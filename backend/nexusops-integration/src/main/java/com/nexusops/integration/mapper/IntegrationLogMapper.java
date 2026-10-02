package com.nexusops.integration.mapper;

import com.nexusops.integration.domain.IntegrationLog;
import com.nexusops.integration.dto.IntegrationLogDto;

/** Converte registros do log de integrações para o contrato da API. */
public final class IntegrationLogMapper {

    private IntegrationLogMapper() {
    }

    public static IntegrationLogDto toDto(IntegrationLog l) {
        return IntegrationLogDto.builder()
            .id(l.getId()).integrationKind(l.getIntegrationKind()).integrationId(l.getIntegrationId())
            .integrationName(l.getIntegrationName()).event(l.getEvent()).outcome(l.getOutcome())
            .httpStatus(l.getHttpStatus()).durationMs(l.getDurationMs()).message(l.getMessage())
            .actor(l.getActor()).createdAt(l.getCreatedAt())
            .build();
    }
}
