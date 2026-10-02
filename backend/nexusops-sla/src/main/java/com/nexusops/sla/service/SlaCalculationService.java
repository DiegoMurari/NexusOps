package com.nexusops.sla.service;

import com.nexusops.sla.domain.BusinessCalendar;
import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.SlaTargetsDto;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.Optional;

/**
 * Cálculo de metas de SLA. Não guarda estado: quem aplica o SLA a um chamado (módulo ticketing) pede as
 * metas daqui e as persiste no ciclo. Os prazos respeitam o calendário comercial da definição (ou o
 * calendário padrão do tenant) e podem ser estendidos quando o relógio fica pausado.
 */
@Service
@RequiredArgsConstructor
public class SlaCalculationService {

    private final SlaDefinitionRepository slaDefinitionRepository;
    private final BusinessCalendarRepository businessCalendarRepository;

    /**
     * Escolhe a definição aplicável e calcula os prazos a partir de {@code start}. Uma definição explícita
     * (por exemplo a da categoria) vale se estiver ativa no tenant; senão vence a mais específica entre as
     * que casam com tipo, categoria, prioridade e perfil do cliente.
     */
    @Transactional(readOnly = true)
    public Optional<SlaTargetsDto> resolveTargets(String tenantId, String type, String categoryId, String priority,
                                                  String customerTier, String explicitDefinitionId, Instant start) {
        SlaDefinition definition = null;
        if (explicitDefinitionId != null) {
            definition = slaDefinitionRepository.findById(explicitDefinitionId)
                .filter(d -> tenantId.equals(d.getTenantId()) && d.isActive())
                .orElse(null);
        }
        if (definition == null) {
            // Parâmetro nulo em comparação JPQL não tem tipo no PostgreSQL: vazio nunca casa com um critério.
            definition = slaDefinitionRepository.findMatchingDefinitions(
                    tenantId, nz(type), nz(categoryId), nz(priority), nz(customerTier)).stream()
                .max(BEST_MATCH)
                .orElse(null);
        }
        if (definition == null) {
            return Optional.empty();
        }

        BusinessCalendar calendar = getBusinessCalendar(definition, tenantId);
        return Optional.of(new SlaTargetsDto(
            definition.getId(),
            definition.getName(),
            definition.getVersion(),
            dueAfter(start, definition.getResponseTimeMinutes(), calendar),
            dueAfter(start, definition.getResolutionTimeMinutes(), calendar),
            definition.isPauseOnHold()));
    }

    /** Mais critérios preenchidos = mais específica; empate: revisão mais nova. */
    private static final Comparator<SlaDefinition> BEST_MATCH = Comparator
        .comparingInt(SlaCalculationService::specificity)
        .thenComparing(SlaDefinition::getVersion)
        .thenComparing(SlaDefinition::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

    private static int specificity(SlaDefinition d) {
        int score = 0;
        if (d.getAppliesToType() != null) score++;
        if (d.getAppliesToCategory() != null) score++;
        if (d.getAppliesToPriority() != null) score++;
        if (d.getAppliesToCustomerTier() != null) score++;
        return score;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    @Transactional(readOnly = true)
    public boolean pausesOnHold(String definitionId) {
        return slaDefinitionRepository.findById(definitionId).map(SlaDefinition::isPauseOnHold).orElse(false);
    }

    /**
     * Estende um prazo pelo tempo em que o relógio ficou pausado: em horário útil quando há calendário
     * (pausa fora do expediente não estende nada), em tempo corrido quando não há.
     */
    @Transactional(readOnly = true)
    public Instant extendDue(String definitionId, String tenantId, Instant due, Instant pausedFrom, Instant pausedTo) {
        if (due == null || !pausedTo.isAfter(pausedFrom)) {
            return due;
        }
        BusinessCalendar calendar = slaDefinitionRepository.findById(definitionId)
            .map(d -> getBusinessCalendar(d, tenantId))
            .orElse(null);
        if (calendar == null) {
            return due.plus(Duration.between(pausedFrom, pausedTo));
        }
        long businessMinutes = businessMinutesBetween(pausedFrom, pausedTo, calendar);
        return businessMinutes <= 0 ? due : dueAfter(due, (int) Math.min(businessMinutes, Integer.MAX_VALUE), calendar);
    }

    @Transactional(readOnly = true)
    public BusinessCalendar getBusinessCalendar(SlaDefinition definition, String tenantId) {
        if (definition.getBusinessCalendarId() != null) {
            return businessCalendarRepository.findById(definition.getBusinessCalendarId()).orElse(null);
        }
        return businessCalendarRepository.findByTenantIdAndDefaultCalendarTrue(tenantId).orElse(null);
    }

    /** Prazo = início + minutos, contando só o expediente quando há calendário. Sem meta devolve nulo. */
    public Instant dueAfter(Instant start, Integer minutes, BusinessCalendar calendar) {
        if (minutes == null || minutes <= 0) {
            return null;
        }

        if (calendar == null) {
            return start.plus(Duration.ofMinutes(minutes));
        }

        ZoneId zone = ZoneId.of(calendar.getTimezone());
        ZonedDateTime zdt = start.atZone(zone);
        long remainingMinutes = minutes;
        // Limite de segurança: um calendário sem nenhum dia útil não pode travar o chamador.
        int guard = 0;

        while (remainingMinutes > 0 && guard++ < 3660) {
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

    /** Minutos de expediente entre dois instantes. */
    public long businessMinutesBetween(Instant from, Instant to, BusinessCalendar calendar) {
        if (!to.isAfter(from)) {
            return 0L;
        }
        if (calendar == null) {
            return Duration.between(from, to).toMinutes();
        }

        ZoneId zone = ZoneId.of(calendar.getTimezone());
        ZonedDateTime dueZdt = to.atZone(zone);
        ZonedDateTime current = from.atZone(zone);
        long total = 0;
        int guard = 0;

        while (current.isBefore(dueZdt) && guard++ < 3660) {
            LocalDate currentDate = current.toLocalDate();
            BusinessCalendar.BusinessHours hours = calendar.getBusinessHours(currentDate);

            if (hours != null && hours.isWorkingDay()) {
                ZonedDateTime dayStart = currentDate.atTime(hours.getStart()).atZone(zone);
                ZonedDateTime dayEnd = currentDate.atTime(hours.getEnd()).atZone(zone);

                if (current.isBefore(dayStart)) {
                    current = dayStart;
                }

                if (current.isBefore(dayEnd) && current.isBefore(dueZdt)) {
                    ZonedDateTime effectiveEnd = dayEnd.isBefore(dueZdt) ? dayEnd : dueZdt;
                    total += Duration.between(current, effectiveEnd).toMinutes();
                }
            }

            current = current.toLocalDate().plusDays(1).atStartOfDay(zone);
        }

        return total;
    }
}
