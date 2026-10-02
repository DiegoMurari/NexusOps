package com.nexusops.integration.dto;

import com.nexusops.integration.domain.WebhookDelivery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Estado de uma entrega. O corpo enviado fica de fora de propósito. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryDto {
    private String id;
    private String webhookId;
    private String eventType;
    private WebhookDelivery.Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private Integer lastHttpStatus;
    private String lastError;
    private Instant createdAt;
    private Instant deliveredAt;
}
