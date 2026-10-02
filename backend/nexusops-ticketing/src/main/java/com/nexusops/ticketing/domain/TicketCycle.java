package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ciclo de atendimento. O SLA é medido por ciclo: o ciclo 1 vai da abertura até a primeira resolução;
 * cada reabertura inicia um novo ciclo, e o ciclo anterior nunca é sobrescrito nem apagado.
 */
@Entity
@Table(name = "ticket_cycles", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCycle implements TenantAware {

    public enum Status { IN_PROGRESS, AWAITING_VALIDATION, ACCEPTED, CONTESTED }

    public enum OpenedReason { CREATED, REOPENED }

    @Id
    @Column(name = "id", length = 36, updatable = false)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36, updatable = false)
    private String ticketId;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "cycle_no", nullable = false, updatable = false)
    private int cycleNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "opened_reason", nullable = false, length = 20, updatable = false)
    private OpenedReason openedReason;

    @Column(name = "reopen_comment", columnDefinition = "text", updatable = false)
    private String reopenComment;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "sla_definition_id", length = 36)
    private String slaDefinitionId;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Builder.Default
    @Column(name = "paused_seconds", nullable = false)
    private long pausedSeconds = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (openedAt == null) {
            openedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
