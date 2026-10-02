package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Solução apresentada pelo técnico em um ciclo. O texto, o autor e a data são imutáveis (o banco
 * também impede a alteração); apenas o desfecho é decidido, uma única vez, pelo solicitante.
 */
@Entity
@Table(name = "ticket_resolutions", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResolution implements TenantAware {

    public enum Outcome { PENDING, ACCEPTED, CONTESTED, AUTO_ACCEPTED }

    @Id
    @Column(name = "id", length = 36, updatable = false)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36, updatable = false)
    private String ticketId;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "cycle_no", nullable = false, updatable = false)
    private int cycleNo;

    @Column(name = "solution_text", columnDefinition = "text", nullable = false, updatable = false)
    private String solutionText;

    @Column(name = "resolved_by", nullable = false, length = 36, updatable = false)
    private String resolvedBy;

    @Column(name = "resolved_at", nullable = false, updatable = false)
    private Instant resolvedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private Outcome outcome = Outcome.PENDING;

    @Column(name = "decided_by", length = 36)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (resolvedAt == null) {
            resolvedAt = Instant.now();
        }
    }
}
