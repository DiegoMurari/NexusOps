package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_relationships", schema = "asset", indexes = {
    @Index(name = "idx_ci_rel_source", columnList = "source_id"),
    @Index(name = "idx_ci_rel_target", columnList = "target_id"),
    @Index(name = "idx_ci_rel_type", columnList = "relationship_type"),
    @Index(name = "idx_ci_rel_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CIRelationship implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "source_id", nullable = false, length = 36)
    private String sourceId;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(name = "relationship_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RelationshipType relationshipType;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }

    public enum RelationshipType {
        DEPENDS_ON,
        CONNECTED_TO,
        HOSTS,
        CONTAINS,
        RUNS_ON,
        MANAGES
    }
}