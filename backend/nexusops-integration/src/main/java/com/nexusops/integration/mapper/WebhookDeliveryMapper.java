package com.nexusops.integration.mapper;

import com.nexusops.integration.domain.WebhookDelivery;
import com.nexusops.integration.dto.WebhookDeliveryDto;

/** Converte entregas para o contrato da API (sem o corpo enviado). */
public final class WebhookDeliveryMapper {

    private WebhookDeliveryMapper() {
    }

    public static WebhookDeliveryDto toDto(WebhookDelivery d) {
        return WebhookDeliveryDto.builder()
            .id(d.getId()).webhookId(d.getWebhookId()).eventType(d.getEventType()).status(d.getStatus())
            .attempts(d.getAttempts()).nextAttemptAt(d.getNextAttemptAt()).lastHttpStatus(d.getLastHttpStatus())
            .lastError(d.getLastError()).createdAt(d.getCreatedAt()).deliveredAt(d.getDeliveredAt())
            .build();
    }
}
