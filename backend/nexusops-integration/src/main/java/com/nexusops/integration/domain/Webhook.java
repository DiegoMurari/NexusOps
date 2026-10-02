package com.nexusops.integration.domain;

import com.nexusops.integration.crypto.WebhookSecretConverter;
import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "webhooks", schema = "integration", indexes = {
    @Index(name = "idx_webhooks_tenant", columnList = "tenant_id"),
    @Index(name = "idx_webhooks_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Webhook implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    /** Signing secret. Write-only: never returned by the API. */
    @Convert(converter = WebhookSecretConverter.class)
    @Column(name = "secret", length = 512)
    private String secret;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "webhook_events", schema = "integration", joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event", length = 100)
    @Builder.Default
    private Set<String> events = new HashSet<>();

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", columnDefinition = "jsonb")
    private String retryPolicy;

    @Column(name = "timeout_seconds", nullable = false)
    @Builder.Default
    private int timeoutSeconds = 30;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private String headers;

    /** Resultado da última entrega (hoje: o evento de teste). SUCCESS ou FAILURE; sem corpo nem segredo. */
    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @Column(name = "last_delivery_status", length = 20)
    private String lastDeliveryStatus;

    @Column(name = "last_delivery_http_status")
    private Integer lastDeliveryHttpStatus;

    /** Categoria curta do erro (ex.: TIMEOUT, BLOCKED_ADDRESS), nunca o texto da exceção. */
    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum WebhookStatus {
        ACTIVE,
        INACTIVE,
        FAILED,
        DISABLED
    }
}
