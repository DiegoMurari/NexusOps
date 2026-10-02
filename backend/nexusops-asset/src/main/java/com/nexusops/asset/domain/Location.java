package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locations", schema = "asset", indexes = {
    @Index(name = "idx_locations_tenant", columnList = "tenant_id"),
    @Index(name = "idx_locations_parent", columnList = "parent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Código estável (ex.: FRANCA), usado por regras de roteamento. Único por tenant, sem diferenciar caixa. */
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Column(name = "type", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LocationType type = LocationType.SITE;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "coordinates", length = 50)
    private String coordinates;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
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

    public enum LocationType {
        SITE,
        BUILDING,
        FLOOR,
        ROOM,
        RACK,
        ROW,
        DATACENTER
    }
}