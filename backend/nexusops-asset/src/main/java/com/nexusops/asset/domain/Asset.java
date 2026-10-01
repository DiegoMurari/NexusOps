package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets", schema = "asset", indexes = {
    @Index(name = "idx_assets_tenant", columnList = "tenant_id"),
    @Index(name = "idx_assets_type", columnList = "type"),
    @Index(name = "idx_assets_status", columnList = "lifecycle_status"),
    @Index(name = "idx_assets_location", columnList = "location_id"),
    @Index(name = "idx_assets_serial", columnList = "serial_number"),
    @Index(name = "idx_assets_assigned", columnList = "assigned_to_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "asset_tag", nullable = false, unique = true, length = 100)
    private String assetTag;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AssetType type;

    @Column(name = "lifecycle_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LifecycleStatus lifecycleStatus = LifecycleStatus.PROCURED;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", columnDefinition = "jsonb")
    private String specifications;

    @Column(name = "location_id", length = 36)
    private String locationId;

    @Column(name = "assigned_to_id", length = 36)
    private String assignedToId;

    @Column(name = "purchase_date")
    private Instant purchaseDate;

    @Column(name = "warranty_expiration")
    private Instant warrantyExpiration;

    @Column(name = "purchase_cost")
    private java.math.BigDecimal purchaseCost;

    @Column(name = "depreciation_method", length = 20)
    private String depreciationMethod;

    @Column(name = "discovery_source", length = 50)
    private String discoverySource;

    @Column(name = "last_discovered_at")
    private Instant lastDiscoveredAt;

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
        if (assetTag == null) {
            assetTag = "AST-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum AssetType {
        HARDWARE,
        SOFTWARE,
        CLOUD,
        VIRTUAL,
        NETWORK,
        STORAGE,
        PERIPHERAL
    }

    public enum LifecycleStatus {
        PROCURED,
        DEPLOYED,
        MAINTENANCE,
        RETIRED,
        DISPOSED,
        LOST,
        STOLEN
    }
}