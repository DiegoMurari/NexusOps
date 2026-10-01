package com.nexusops.sla.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sla_breaches", schema = "sla", indexes = {
    @Index(name = "idx_sla_breaches_ticket", columnList = "ticket_id"),
    @Index(name = "idx_sla_breaches_tenant", columnList = "tenant_id"),
    @Index(name = "idx_sla_breaches_status", columnList = "status"),
    @Index(name = "idx_sla_breaches_breach_time", columnList = "breach_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaBreach implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36)
    private String ticketId;

    @Column(name = "sla_definition_id", nullable = false, length = 36)
    private String slaDefinitionId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "breach_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BreachType breachType;

    @Column(name = "breach_time", nullable = false)
    private Instant breachTime;

    @Builder.Default
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;

    @Column(name = "acknowledged_by", length = 36)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Builder.Default
    @Column(name = "escalated", nullable = false)
    private boolean escalated = false;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Builder.Default
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "response_time_minutes")
    private Integer responseTimeMinutes;

    @Column(name = "resolution_time_minutes")
    private Integer resolutionTimeMinutes;

    @Column(name = "breach_percentage")
    private Integer breachPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public enum BreachType {
        RESPONSE,
        RESOLUTION
    }
}