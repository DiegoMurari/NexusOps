package com.nexusops.sla.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "sla_definitions", schema = "sla", indexes = {
    @Index(name = "idx_sla_definitions_tenant", columnList = "tenant_id"),
    @Index(name = "idx_sla_definitions_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaDefinition implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "applies_to_type", length = 50)
    private String appliesToType;

    @Column(name = "applies_to_category", length = 100)
    private String appliesToCategory;

    @Column(name = "applies_to_priority", length = 50)
    private String appliesToPriority;

    @Column(name = "applies_to_customer_tier", length = 50)
    private String appliesToCustomerTier;

    @Column(name = "response_time_minutes")
    private Integer responseTimeMinutes;

    @Column(name = "resolution_time_minutes")
    private Integer resolutionTimeMinutes;

    @Column(name = "business_calendar_id", length = 36)
    private String businessCalendarId;

    @Builder.Default
    @Column(name = "pause_on_hold", nullable = false)
    private boolean pauseOnHold = true;

    @Builder.Default
    @Column(name = "stop_on_first_response", nullable = false)
    private boolean stopOnFirstResponse = true;

    @Builder.Default
    @Column(name = "breach_warning_percentage_80", nullable = false)
    private boolean breachWarning80 = true;

    @Builder.Default
    @Column(name = "breach_warning_percentage_90", nullable = false)
    private boolean breachWarning90 = true;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

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
}