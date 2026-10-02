package com.nexusops.integration.mapper;

import com.nexusops.integration.domain.Webhook;
import com.nexusops.integration.dto.WebhookDto;

import java.util.TreeSet;

/** Converte webhooks entre o domínio e o contrato da API (o segredo nunca sai, só se existe). */
public final class WebhookMapper {

    private WebhookMapper() {
    }

    public static WebhookDto toDto(Webhook w) {
        return WebhookDto.builder()
            .id(w.getId()).name(w.getName()).targetUrl(w.getTargetUrl())
            .events(new TreeSet<>(w.getEvents())).status(w.getStatus())
            .timeoutSeconds(w.getTimeoutSeconds())
            .hasSecret(w.getSecret() != null && !w.getSecret().isBlank())
            .createdAt(w.getCreatedAt()).updatedAt(w.getUpdatedAt())
            .build();
    }
}
