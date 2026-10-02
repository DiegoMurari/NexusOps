package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Regra de roteamento: se todas as condições casam, aplica as ações (fila, prioridade, responsável). */
@Entity
@Table(name = "routing_rules", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingRule implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "position", nullable = false)
    private int position;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Lista de condições serializada (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", nullable = false, columnDefinition = "jsonb")
    private String conditions;

    /** Ações serializadas (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", nullable = false, columnDefinition = "jsonb")
    private String actions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
