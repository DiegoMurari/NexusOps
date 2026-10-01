package com.nexusops.ticketing.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_entries", schema = "ticketing", indexes = {
    @Index(name = "idx_time_entries_ticket", columnList = "ticket_id"),
    @Index(name = "idx_time_entries_user", columnList = "user_id"),
    @Index(name = "idx_time_entries_date", columnList = "start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "ticket_id", nullable = false, length = 36)
    private String ticketId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Builder.Default
    @Column(name = "billable", nullable = false)
    private boolean billable = true;

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
        if (endTime != null && startTime != null) {
            durationMinutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        if (endTime != null && startTime != null) {
            durationMinutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        }
    }
}