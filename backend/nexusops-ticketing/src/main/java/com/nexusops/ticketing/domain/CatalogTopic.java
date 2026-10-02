package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tópico do catálogo: o que o solicitante escolhe ("Falha de conexão"). Carrega os padrões internos que o
 * sistema aplica ao chamado: fila, prioridade inicial e SLA. O solicitante nunca vê esses padrões.
 */
@Entity
@Table(name = "catalog_topics", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogTopic implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "area_id", nullable = false, length = 36)
    private String areaId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "default_queue_id", length = 36)
    private String defaultQueueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", length = 20)
    private Ticket.Priority defaultPriority;

    @Column(name = "sla_definition_id", length = 36)
    private String slaDefinitionId;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

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
