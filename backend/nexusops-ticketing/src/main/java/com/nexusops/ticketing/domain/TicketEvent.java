package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento da timeline do chamado. Append-only: a entidade é imutável e o banco rejeita UPDATE e DELETE.
 */
@Entity
@Immutable
@Table(name = "ticket_events", schema = "ticketing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TicketEvent implements TenantAware {

    public enum ActorKind { USER, SYSTEM, RULE }

    public enum Visibility { PUBLIC, INTERNAL }

    @Id
    @Column(name = "id", length = 36, updatable = false)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36, updatable = false)
    private String ticketId;

    @Column(name = "tenant_id", nullable = false, length = 36, updatable = false)
    private String tenantId;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Column(name = "cycle_no", nullable = false, updatable = false)
    private int cycleNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private TicketEventType eventType;

    @Column(name = "actor_id", length = 36, updatable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 10, updatable = false)
    private ActorKind actorKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10, updatable = false)
    private Visibility visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public static TicketEventBuilder newEvent() {
        return TicketEvent.builder().occurredAt(Instant.now());
    }
}
