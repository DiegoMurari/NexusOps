package com.nexusops.sla.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "business_calendars", schema = "sla", indexes = {
    @Index(name = "idx_business_calendars_tenant", columnList = "tenant_id"),
    @Index(name = "idx_business_calendars_default", columnList = "default_calendar")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessCalendar implements TenantAware {

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
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "UTC";

    @Builder.Default
    @Column(name = "default_calendar", nullable = false)
    private boolean defaultCalendar = false;

    @ElementCollection
    @CollectionTable(name = "business_hours", schema = "sla", joinColumns = @JoinColumn(name = "calendar_id"))
    @MapKeyColumn(name = "day_of_week")
    @MapKeyEnumerated(EnumType.STRING)
    @Builder.Default
    private Map<DayOfWeek, BusinessHours> businessHours = new EnumMap<>(DayOfWeek.class);

    @ElementCollection
    @CollectionTable(name = "holidays", schema = "sla", joinColumns = @JoinColumn(name = "calendar_id"))
    @Column(name = "holiday_date")
    @Builder.Default
    private Set<LocalDate> holidays = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "calendar_exceptions", schema = "sla", joinColumns = @JoinColumn(name = "calendar_id"))
    @MapKeyColumn(name = "exception_date")
    @Builder.Default
    private Map<LocalDate, BusinessHours> exceptions = new HashMap<>();

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
        
        // Set default business hours (Mon-Fri 9-17)
        if (businessHours.isEmpty()) {
            BusinessHours defaultHours = BusinessHours.builder()
                .start(LocalTime.of(9, 0))
                .end(LocalTime.of(17, 0))
                .build();
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                    businessHours.put(day, defaultHours);
                }
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isBusinessDay(LocalDate date) {
        if (holidays.contains(date)) {
            return false;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return businessHours.containsKey(dayOfWeek) && businessHours.get(dayOfWeek) != null;
    }

    public BusinessHours getBusinessHours(LocalDate date) {
        if (exceptions.containsKey(date)) {
            return exceptions.get(date);
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return businessHours.get(dayOfWeek);
    }

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessHours {
        @Column(name = "start_time")
        private LocalTime start;

        @Column(name = "end_time")
        private LocalTime end;

        @Column(name = "working_day")
        @Builder.Default
        private boolean workingDay = true;
    }
}