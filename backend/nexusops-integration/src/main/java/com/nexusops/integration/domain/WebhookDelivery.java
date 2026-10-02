package com.nexusops.integration.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrega pendente ou concluída de um evento do sistema a um webhook (outbox). A linha nasce na mesma
 * hora do evento, e um worker a envia com reentrega e espera crescente. O mesmo evento nunca é enfileirado
 * duas vezes para o mesmo webhook ({@code uk_webhook_delivery_event}).
 */
@Entity
@Table(name = "webhook_deliveries", schema = "integration", indexes = {
    @Index(name = "idx_webhook_deliveries_due", columnList = "status, next_attempt_at"),
    @Index(name = "idx_webhook_deliveries_tenant", columnList = "tenant_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "webhook_id", nullable = false, length = 36)
    private String webhookId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** Corpo JSON exatamente como será enviado (e assinado). Nunca é devolvido pela API. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    /** Categoria curta do erro (TIMEOUT, HTTP_500...), nunca texto livre do destino. */
    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status {
        PENDING,
        SENDING,
        DELIVERED,
        FAILED,
        CANCELLED
    }
}
