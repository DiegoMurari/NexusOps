package com.nexusops.ticketing.service;

import com.nexusops.sla.dto.SlaTargetsDto;
import com.nexusops.sla.service.SlaCalculationService;
import com.nexusops.ticketing.domain.Incident;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.CatalogTopicRepository;
import com.nexusops.ticketing.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSlaServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    @Mock SlaCalculationService calc;
    @Mock CategoryRepository categories;
    @Mock CatalogTopicRepository topics;
    @Mock TicketCycleService cycles;

    private TicketSlaService service;

    @BeforeEach
    void setUp() {
        service = new TicketSlaService(calc, categories, topics, cycles);
    }

    private static Ticket ticket() {
        Ticket t = Incident.builder().tenantId("t1").priority(Ticket.Priority.HIGH).build();
        t.setCreatedAt(T0);
        return t;
    }

    private static SlaTargetsDto targets(Instant response, Instant resolution) {
        return new SlaTargetsDto("def1", "Padrão", 1, response, resolution, true);
    }

    @Test
    void applyOnCreateStoresDefinitionAndDueDates() {
        when(calc.resolveTargets(eq("t1"), eq("INCIDENT"), any(), eq("HIGH"), any(), any(), eq(T0)))
            .thenReturn(Optional.of(targets(T0.plusSeconds(900), T0.plusSeconds(14400))));
        Ticket t = ticket();

        service.applyOnCreate(t);

        assertThat(t.getSlaDefinitionId()).isEqualTo("def1");
        assertThat(t.getResponseDueAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(t.getResolutionDueAt()).isEqualTo(T0.plusSeconds(14400));
    }

    @Test
    void withoutMatchingDefinitionTicketHasNoTargets() {
        when(calc.resolveTargets(anyString(), anyString(), any(), anyString(), any(), any(), any()))
            .thenReturn(Optional.empty());
        Ticket t = ticket();
        t.setSlaDefinitionId("old");
        t.setResolutionDueAt(T0);

        service.applyOnCreate(t);

        assertThat(t.getSlaDefinitionId()).isNull();
        assertThat(t.getResponseDueAt()).isNull();
        assertThat(t.getResolutionDueAt()).isNull();
    }

    @Test
    void pauseOnlyWhenDefinitionAsksForIt() {
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");
        when(calc.pausesOnHold("def1")).thenReturn(false);

        service.pause(t, T0);

        assertThat(t.getSlaPausedAt()).isNull();

        when(calc.pausesOnHold("def1")).thenReturn(true);
        service.pause(t, T0);

        assertThat(t.getSlaPausedAt()).isEqualTo(T0);
    }

    @Test
    void pauseKeepsTheOriginalInstantWhenAlreadyPaused() {
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");
        t.setSlaPausedAt(T0);

        service.pause(t, T0.plusSeconds(60));

        assertThat(t.getSlaPausedAt()).isEqualTo(T0);
        verify(calc, never()).pausesOnHold(anyString());
    }

    @Test
    void resumeExtendsDeadlinesAndAccountsPausedTime() {
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");
        t.setSlaPausedAt(T0);
        t.setResponseDueAt(T0.plusSeconds(900));
        t.setResolutionDueAt(T0.plusSeconds(14400));
        Instant now = T0.plusSeconds(600);
        when(calc.extendDue("def1", "t1", T0.plusSeconds(900), T0, now)).thenReturn(T0.plusSeconds(1500));
        when(calc.extendDue("def1", "t1", T0.plusSeconds(14400), T0, now)).thenReturn(T0.plusSeconds(15000));

        service.resume(t, now);

        assertThat(t.getSlaPausedAt()).isNull();
        assertThat(t.getResponseDueAt()).isEqualTo(T0.plusSeconds(1500));
        assertThat(t.getResolutionDueAt()).isEqualTo(T0.plusSeconds(15000));
        verify(cycles).syncSla(t, Duration.between(T0, now).getSeconds());
    }

    @Test
    void resumeDoesNotMoveResponseDeadlineAfterFirstResponse() {
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");
        t.setSlaPausedAt(T0);
        t.setFirstResponseAt(T0.plusSeconds(60));
        t.setResponseDueAt(T0.plusSeconds(900));
        t.setResolutionDueAt(T0.plusSeconds(14400));
        Instant now = T0.plusSeconds(600);
        when(calc.extendDue("def1", "t1", T0.plusSeconds(14400), T0, now)).thenReturn(T0.plusSeconds(15000));

        service.resume(t, now);

        assertThat(t.getResponseDueAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(t.getResolutionDueAt()).isEqualTo(T0.plusSeconds(15000));
    }

    @Test
    void resumeWhenNotPausedDoesNothing() {
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");

        service.resume(t, T0);

        verify(calc, never()).extendDue(anyString(), anyString(), any(), any(), any());
        verify(cycles, never()).syncSla(any(), anyLong());
    }

    @Test
    void newCycleRestartsTheClockAndFirstResponse() {
        Instant now = T0.plusSeconds(86400);
        when(calc.resolveTargets(eq("t1"), eq("INCIDENT"), any(), eq("HIGH"), any(), any(), eq(now)))
            .thenReturn(Optional.of(targets(now.plusSeconds(900), now.plusSeconds(14400))));
        Ticket t = ticket();
        t.setSlaDefinitionId("def1");
        t.setSlaPausedAt(T0);
        t.setFirstResponseAt(T0.plusSeconds(60));

        service.restartForNewCycle(t, now);

        assertThat(t.getSlaPausedAt()).isNull();
        assertThat(t.getFirstResponseAt()).isNull();
        assertThat(t.getResolutionDueAt()).isEqualTo(now.plusSeconds(14400));
        verify(cycles).syncSla(t, 0);
    }
}
