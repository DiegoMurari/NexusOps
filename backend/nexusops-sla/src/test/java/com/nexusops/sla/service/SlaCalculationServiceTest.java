package com.nexusops.sla.service;

import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.SlaTargetsDto;
import com.nexusops.sla.repository.BusinessCalendarRepository;
import com.nexusops.sla.repository.SlaDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaCalculationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final Instant START = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private SlaDefinitionRepository definitions;
    @Mock
    private BusinessCalendarRepository calendars;

    private SlaCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SlaCalculationService(definitions, calendars);
    }

    private SlaDefinition definition(String id, String type, String priority, Integer response, Integer resolution, int version) {
        return SlaDefinition.builder().id(id).name(id).tenantId(TENANT).active(true)
            .appliesToType(type).appliesToPriority(priority)
            .responseTimeMinutes(response).resolutionTimeMinutes(resolution).version(version).build();
    }

    private void matching(SlaDefinition... found) {
        when(definitions.findMatchingDefinitions(eq(TENANT), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(found));
    }

    @Test
    void resolveTargets_picksTheMostSpecificDefinition_notTheNewestRevision() {
        SlaDefinition generic = definition("generic", null, null, 60, 480, 9);
        SlaDefinition specific = definition("specific", "INCIDENT", "HIGH", 15, 120, 1);
        matching(generic, specific);

        SlaTargetsDto targets = service.resolveTargets(TENANT, "INCIDENT", null, "HIGH", null, null, START).orElseThrow();

        assertThat(targets.definitionId()).isEqualTo("specific");
        assertThat(targets.responseDueAt()).isEqualTo(START.plus(Duration.ofMinutes(15)));
        assertThat(targets.resolutionDueAt()).isEqualTo(START.plus(Duration.ofMinutes(120)));
    }

    @Test
    void resolveTargets_withoutAnyMatch_isEmpty() {
        matching();

        assertThat(service.resolveTargets(TENANT, "INCIDENT", null, "LOW", null, null, START)).isEmpty();
    }

    @Test
    void resolveTargets_anActiveExplicitDefinitionOfTheTenantWins() {
        when(definitions.findById("explicit")).thenReturn(Optional.of(definition("explicit", null, null, 5, 30, 1)));

        SlaTargetsDto targets = service.resolveTargets(TENANT, "INCIDENT", "cat", "LOW", null, "explicit", START).orElseThrow();

        assertThat(targets.definitionId()).isEqualTo("explicit");
        verify(definitions, never()).findMatchingDefinitions(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resolveTargets_ignoresAnExplicitDefinitionFromAnotherTenantOrInactive() {
        SlaDefinition foreign = definition("foreign", null, null, 5, 30, 1);
        foreign.setTenantId("tenant-2");
        when(definitions.findById("foreign")).thenReturn(Optional.of(foreign));
        SlaDefinition inactive = definition("inactive", null, null, 5, 30, 1);
        inactive.setActive(false);
        when(definitions.findById("inactive")).thenReturn(Optional.of(inactive));
        matching(definition("fallback", null, null, 10, 60, 1));

        assertThat(service.resolveTargets(TENANT, "INCIDENT", null, "LOW", null, "foreign", START).orElseThrow().definitionId())
            .isEqualTo("fallback");
        assertThat(service.resolveTargets(TENANT, "INCIDENT", null, "LOW", null, "inactive", START).orElseThrow().definitionId())
            .isEqualTo("fallback");
    }

    @Test
    void resolveTargets_aMissingTargetMeansNoDueDate() {
        matching(definition("only-resolution", null, null, null, 90, 1));

        SlaTargetsDto targets = service.resolveTargets(TENANT, "INCIDENT", null, "LOW", null, null, START).orElseThrow();

        assertThat(targets.responseDueAt()).isNull();
        assertThat(targets.resolutionDueAt()).isEqualTo(START.plus(Duration.ofMinutes(90)));
    }

    @Test
    void extendDue_withoutCalendar_addsTheWholePausedTime() {
        when(definitions.findById("d")).thenReturn(Optional.of(definition("d", null, null, 10, 60, 1)));
        Instant due = START.plus(Duration.ofMinutes(60));
        Instant pausedFrom = START.plus(Duration.ofMinutes(20));
        Instant pausedTo = pausedFrom.plus(Duration.ofMinutes(45));

        assertThat(service.extendDue("d", TENANT, due, pausedFrom, pausedTo)).isEqualTo(due.plus(Duration.ofMinutes(45)));
    }

    @Test
    void extendDue_isANoOpWhenThereWasNoPauseOrNoDueDate() {
        Instant due = START.plus(Duration.ofMinutes(60));

        assertThat(service.extendDue("d", TENANT, due, START, START)).isEqualTo(due);
        assertThat(service.extendDue("d", TENANT, null, START, START.plusSeconds(60))).isNull();
    }
}
