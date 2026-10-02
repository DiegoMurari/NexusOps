package com.nexusops.integration.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro do que aconteceu com uma integração (criada, alterada, ligada/desligada, testada, verificada).
 * Guarda só o fato e o resultado: nunca segredo, corpo de requisição ou URL completa. Fica depois que a
 * integração é excluída (o nome é copiado), porque o histórico existe justamente para isso.
 */
@Entity
@Table(name = "integration_logs", schema = "integration", indexes = {
    @Index(name = "idx_integration_logs_tenant_created", columnList = "tenant_id, created_at"),
    @Index(name = "idx_integration_logs_integration", columnList = "tenant_id, integration_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationLog implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "integration_kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Kind integrationKind;

    @Column(name = "integration_id", nullable = false, length = 36)
    private String integrationId;

    @Column(name = "integration_name", nullable = false, length = 255)
    private String integrationName;

    @Column(name = "event", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Event event;

    @Column(name = "outcome", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }

    public enum Kind {
        WEBHOOK,
        CONNECTOR
    }

    public enum Event {
        CREATED,
        UPDATED,
        ENABLED,
        DISABLED,
        DELETED,
        TEST,
        CHECK
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
