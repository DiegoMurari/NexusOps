package com.nexusops.sla.dto;

import com.nexusops.sla.domain.BusinessCalendar;
import lombok.*;

import java.time.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessCalendarDto {

    private String id;
    private String name;
    private String description;
    private String tenantId;
    private String timezone;
    private boolean defaultCalendar;
    private Map<DayOfWeek, BusinessHoursDto> businessHours;
    private Set<LocalDate> holidays;
    private Map<LocalDate, BusinessHoursDto> exceptions;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessHoursDto {
        private LocalTime start;
        private LocalTime end;
        private boolean workingDay = true;
    }
}