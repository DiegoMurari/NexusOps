package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "software_licenses", schema = "asset", indexes = {
    @Index(name = "idx_sw_lic_tenant", columnList = "tenant_id"),
    @Index(name = "idx_sw_lic_product", columnList = "product_name"),
    @Index(name = "idx_sw_lic_compliance", columnList = "compliance_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareLicense implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "vendor", length = 100)
    private String vendor;

    @Column(name = "product_version", length = 50)
    private String productVersion;

    @Column(name = "license_type", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LicenseType licenseType = LicenseType.PERPETUAL;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "used_seats", nullable = false)
    @Builder.Default
    private int usedSeats = 0;

    @Column(name = "compliance_status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplianceStatus complianceStatus = ComplianceStatus.COMPLIANT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlements", columnDefinition = "jsonb")
    private String entitlements;

    @Column(name = "purchase_date")
    private Instant purchaseDate;

    @Column(name = "expiration_date")
    private Instant expirationDate;

    @Column(name = "renewal_date")
    private Instant renewalDate;

    @Column(name = "cost")
    private java.math.BigDecimal cost;

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
        updateComplianceStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        updateComplianceStatus();
    }

    private void updateComplianceStatus() {
        if (usedSeats > totalSeats) {
            complianceStatus = ComplianceStatus.NON_COMPLIANT;
        } else if (usedSeats == totalSeats) {
            complianceStatus = ComplianceStatus.AT_CAPACITY;
        } else if (usedSeats >= totalSeats * 0.9) {
            complianceStatus = ComplianceStatus.AT_RISK;
        } else {
            complianceStatus = ComplianceStatus.COMPLIANT;
        }
    }

    public double getUtilizationPercentage() {
        return totalSeats > 0 ? (double) usedSeats / totalSeats * 100 : 0;
    }

    public enum LicenseType {
        PERPETUAL,
        SUBSCRIPTION,
        CONCURRENT,
        NAMED_USER,
        DEVICE,
        CORE,
        SOCKET
    }

    public enum ComplianceStatus {
        COMPLIANT,
        AT_RISK,
        AT_CAPACITY,
        NON_COMPLIANT
    }
}