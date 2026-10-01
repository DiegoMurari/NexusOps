package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "tickets", schema = "ticketing", indexes = {
    @Index(name = "idx_tickets_tenant", columnList = "tenant_id"),
    @Index(name = "idx_tickets_number", columnList = "ticket_number", unique = true),
    @Index(name = "idx_tickets_status", columnList = "status"),
    @Index(name = "idx_tickets_priority", columnList = "priority"),
    @Index(name = "idx_tickets_assignee", columnList = "assignee_id"),
    @Index(name = "idx_tickets_reporter", columnList = "reporter_id"),
    @Index(name = "idx_tickets_category", columnList = "category_id"),
    @Index(name = "idx_tickets_created", columnList = "created_at")
})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "ticket_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class Ticket implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
    private String ticketNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.OPEN;

    @Builder.Default
    @Column(name = "priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "urgency", length = 20)
    @Enumerated(EnumType.STRING)
    private Urgency urgency;

    @Column(name = "impact", length = 20)
    @Enumerated(EnumType.STRING)
    private Impact impact;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "assignee_id", length = 36)
    private String assigneeId;

    @Column(name = "reporter_id", nullable = false, length = 36)
    private String reporterId;

    @Column(name = "group_id", length = 36)
    private String groupId;

    @Column(name = "sla_definition_id", length = 36)
    private String slaDefinitionId;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "ci_reference", length = 100)
    private String ciReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private String customFields;

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
        if (ticketNumber == null) {
            ticketNumber = generateTicketNumber();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private String generateTicketNumber() {
        return "TKT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public boolean isOpen() {
        return status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS || status == TicketStatus.WAITING;
    }

    public boolean isResolved() {
        return status == TicketStatus.RESOLVED;
    }

    public boolean isClosed() {
        return status == TicketStatus.CLOSED;
    }

    public enum TicketStatus {
        OPEN,
        IN_PROGRESS,
        WAITING,
        ON_HOLD,
        RESOLVED,
        CLOSED,
        REOPENED
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Urgency {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum Impact {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}