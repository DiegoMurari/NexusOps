package com.nexusops.platform.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants", schema = "platform", indexes = {
    @Index(name = "idx_tenants_domain", columnList = "domain", unique = true),
    @Index(name = "idx_tenants_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "domain", nullable = false, unique = true, length = 255)
    private String domain;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Builder.Default
    @Column(name = "subscription_tier", length = 50)
    private String subscriptionTier = "FREE";

    @Column(name = "subscription_expires")
    private Instant subscriptionExpires;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    @Builder.Default
    private String settings = "{}";

    @Builder.Default
    @Column(name = "max_users", nullable = false)
    private int maxUsers = 100;

    @Builder.Default
    @Column(name = "max_assets", nullable = false)
    private int maxAssets = 1000;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_name", length = 255)
    private String contactName;

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

    @Override
    public String getTenantId() {
        return id;
    }

    public enum TenantStatus {
        ACTIVE, SUSPENDED, TRIAL, EXPIRED, DELETED
    }
}