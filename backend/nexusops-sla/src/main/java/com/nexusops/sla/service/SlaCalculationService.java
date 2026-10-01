package com.nexusops.sla.service;

import com.nexusops.sla.domain.BusinessCalendar;
import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.SlaCalculationResultDto;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.sla.repository.SlaBreachRepository;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlaCalculationService {

    private final SlaDefinitionRepository slaDefinitionRepository;
    private final BusinessCalendarRepository businessCalendarRepository;
    private final SlaBreachRepository slaBreachRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "sla-calculations", key = "#ticketId + ':' + #tenantId", unless = "#result == null")
    public SlaCalculationResultDto calculateSlaForTicket(String ticketId, String tenantId, Instant createdAt,
                                                         String type, String category, String priority,
                                                         String customerTier, String status,
                                                         Instant firstResponseAt, Instant resolvedAt) {
        
        List<SlaDefinition> definitions = slaDefinitionRepository.findMatchingDefinitions(
            tenantId, type, category, priority, customerTier);

        if (definitions.isEmpty()) {
            return null;
        }

        SlaDefinition definition = definitions.get(0);
        BusinessCalendar calendar = getBusinessCalendar(definition, tenantId);

        Instant now = Instant.now();
        Instant responseDueAt = calculateDueTime(createdAt, definition.getResponseTimeMinutes(), calendar);
        Instant resolutionDueAt = calculateDueTime(createdAt, definition.getResolutionTimeMinutes(), calendar);

        boolean paused = definition.isPauseOnHold() && "ON_HOLD".equals(status);
        Instant pausedAt = null;

        Long responseRemainingMinutes = null;
        Long resolutionRemainingMinutes = null;
        Integer responsePercentage = null;
        Integer resolutionPercentage = null;
        Boolean responseBreachImminent = false;
        Boolean resolutionBreachImminent = false;

        if (!paused) {
            if (firstResponseAt != null) {
                responseRemainingMinutes = 0L;
                responsePercentage = 100;
            } else if (definition.getResponseTimeMinutes() != null) {
                responseRemainingMinutes = calculateRemainingMinutes(now, responseDueAt, calendar, paused);
                if (definition.getResponseTimeMinutes() > 0) {
                    long total = definition.getResponseTimeMinutes();
                    long elapsed = total - responseRemainingMinutes;
                    responsePercentage = (int) ((elapsed * 100) / total);
                    responseBreachImminent = responsePercentage >= 80 && responsePercentage < 100;
                }
            }

            if (resolvedAt != null) {
                resolutionRemainingMinutes = 0L;
                resolutionPercentage = 100;
            } else if (definition.getResolutionTimeMinutes() != null) {
                resolutionRemainingMinutes = calculateRemainingMinutes(now, resolutionDueAt, calendar, paused);
                if (definition.getResolutionTimeMinutes() > 0) {
                    long total = definition.getResolutionTimeMinutes();
                    long elapsed = total - resolutionRemainingMinutes;
                    resolutionPercentage = (int) ((elapsed * 100) / total);
                    resolutionBreachImminent = resolutionPercentage >= 80 && resolutionPercentage < 100;
                }
            }
        }

        return SlaCalculationResultDto.builder()
            .ticketId(ticketId)
            .slaDefinitionId(definition.getId())
            .responseDueAt(responseDueAt)
            .resolutionDueAt(resolutionDueAt)
            .responseRemainingMinutes(responseRemainingMinutes)
            .resolutionRemainingMinutes(resolutionRemainingMinutes)
            .responsePercentage(responsePercentage)
            .resolutionPercentage(resolutionPercentage)
            .responseBreachImminent(responseBreachImminent)
            .resolutionBreachImminent(resolutionBreachImminent)
            .paused(paused)
            .pausedAt(pausedAt)
            .build();
    }

    @Transactional(readOnly = true)
    public BusinessCalendar getBusinessCalendar(SlaDefinition definition, String tenantId) {
        if (definition.getBusinessCalendarId() != null) {
            return businessCalendarRepository.findById(definition.getBusinessCalendarId()).orElse(null);
        }
        return businessCalendarRepository.findByTenantIdAndDefaultCalendarTrue(tenantId).orElse(null);
    }

    private Instant calculateDueTime(Instant start, Integer minutes, BusinessCalendar calendar) {
        if (minutes == null || minutes <= 0) {
            return null;
        }

        if (calendar == null) {
            return start.plus(Duration.ofMinutes(minutes));
        }

        ZoneId zone = ZoneId.of(calendar.getTimezone());
        ZonedDateTime zdt = start.atZone(zone);
        long remainingMinutes = minutes;

        while (remainingMinutes > 0) {
            LocalDate currentDate = zdt.toLocalDate();
            BusinessCalendar.BusinessHours hours = calendar.getBusinessHours(currentDate);

            if (hours != null && hours.isWorkingDay()) {
                LocalTime startTime = hours.getStart();
                LocalTime endTime = hours.getEnd();

                ZonedDateTime dayStart = currentDate.atTime(startTime).atZone(zone);
                ZonedDateTime dayEnd = currentDate.atTime(endTime).atZone(zone);

                if (zdt.isBefore(dayStart)) {
                    zdt = dayStart;
                }

                if (zdt.isBefore(dayEnd)) {
                    long minutesInDay = Duration.between(zdt, dayEnd).toMinutes();
                    if (remainingMinutes <= minutesInDay) {
                        return zdt.plusMinutes(remainingMinutes).toInstant();
                    }
                    remainingMinutes -= minutesInDay;
                }
            }

            zdt = zdt.toLocalDate().plusDays(1).atStartOfDay(zone);
        }

        return zdt.toInstant();
    }

    private Long calculateRemainingMinutes(Instant now, Instant dueAt, BusinessCalendar calendar, boolean paused) {
        if (paused || dueAt == null) {
            return null;
        }

        if (now.isAfter(dueAt)) {
            return 0L;
        }

        if (calendar == null) {
            return Duration.between(now, dueAt).toMinutes();
        }

        ZoneId zone = ZoneId.of(calendar.getTimezone());
        ZonedDateTime nowZdt = now.atZone(zone);
        ZonedDateTime dueZdt = dueAt.atZone(zone);

        long remaining = 0;
        ZonedDateTime current = nowZdt;

        while (current.isBefore(dueZdt)) {
            LocalDate currentDate = current.toLocalDate();
            BusinessCalendar.BusinessHours hours = calendar.getBusinessHours(currentDate);

            if (hours != null && hours.isWorkingDay()) {
                LocalTime startTime = hours.getStart();
                LocalTime endTime = hours.getEnd();

                ZonedDateTime dayStart = currentDate.atTime(startTime).atZone(zone);
                ZonedDateTime dayEnd = currentDate.atTime(endTime).atZone(zone);

                if (current.isBefore(dayStart)) {
                    current = dayStart;
                }

                if (current.isBefore(dayEnd) && current.isBefore(dueZdt)) {
                    ZonedDateTime effectiveEnd = dayEnd.isBefore(dueZdt) ? dayEnd : dueZdt;
                    remaining += Duration.between(current, effectiveEnd).toMinutes();
                }
            }

            current = current.toLocalDate().plusDays(1).atStartOfDay(zone);
        }

        return remaining;
    }

    @Transactional
    public void startSlaTimer(String ticketId) {
        log.debug("Starting SLA timer for ticket: {}", ticketId);
    }

    @Transactional
    public void pauseSlaTimer(String ticketId) {
        log.debug("Pausing SLA timer for ticket: {}", ticketId);
    }

    @Transactional
    public void resumeSlaTimer(String ticketId) {
        log.debug("Resuming SLA timer for ticket: {}", ticketId);
    }

    @Transactional
    public void stopSlaTimer(String ticketId) {
        log.debug("Stopping SLA timer for ticket: {}", ticketId);
    }

    @Transactional
    public void checkAndCreateBreaches(String tenantId) {
        List<SlaBreach> unacknowledged = slaBreachRepository.findUnacknowledgedUnescalated(tenantId);
        Instant now = Instant.now();

        for (SlaBreach breach : unacknowledged) {
            if (breach.getBreachTime().isBefore(now)) {
                if (!breach.isEscalated()) {
                    breach.setEscalated(true);
                    breach.setEscalatedAt(now);
                    slaBreachRepository.save(breach);
                }
            }
        }
    }
}