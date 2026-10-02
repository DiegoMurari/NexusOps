package com.nexusops.ticketing.service;

import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.service.SlaBreachService;
import com.nexusops.ticketing.domain.Incident;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketCycle;
import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import com.nexusops.ticketing.repository.TicketEventRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaWatchServiceTest {

    private static final Instant START = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant DUE = START.plusSeconds(10_000);

    @Mock TicketRepository tickets;
    @Mock TicketEventRepository events;
    @Mock TicketTimelineService timeline;
    @Mock TicketSlaService ticketSla;
    @Mock TicketCycleService cycles;
    @Mock SlaBreachService breaches;
    @Mock PlatformTransactionManager tx;

    private SlaWatchService service;

    @BeforeEach
    void setUp() {
        service = new SlaWatchService(tickets, events, timeline, ticketSla, cycles, breaches, tx);
    }

    private Ticket watched() {
        Ticket t = Incident.builder().id("tk1").tenantId("t1").status(Ticket.TicketStatus.IN_PROGRESS).build();
        t.setSlaDefinitionId("def1");
        t.setResponseDueAt(START.plusSeconds(900));
        t.setFirstResponseAt(START.plusSeconds(60));
        t.setResolutionDueAt(DUE);
        t.setCycleNo(1);
        when(tickets.findById("tk1")).thenReturn(Optional.of(t));
        return t;
    }

    private void cycleStartedAtStart(long pausedSeconds) {
        TicketCycle c = TicketCycle.builder().openedAt(START).pausedSeconds(pausedSeconds).build();
        when(cycles.findCurrent(any())).thenReturn(Optional.of(c));
    }

    @Test
    void ignoresPausedTickets() {
        Ticket t = watched();
        t.setSlaPausedAt(START);

        assertThat(service.evaluate("tk1", DUE.plusSeconds(1))).isFalse();
        verify(breaches, never()).recordBreach(anyString(), anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void ignoresTicketsOutOfAttendance() {
        Ticket t = watched();
        t.setStatus(Ticket.TicketStatus.RESOLVED);

        assertThat(service.evaluate("tk1", DUE.plusSeconds(1))).isFalse();
    }

    @Test
    void recordsResolutionBreachOnceTheDeadlinePasses() {
        watched();
        when(breaches.recordBreach("tk1", "def1", "t1", SlaBreach.BreachType.RESOLUTION, DUE, 100)).thenReturn(true);

        assertThat(service.evaluate("tk1", DUE.plusSeconds(1))).isTrue();
        verify(timeline).append(any(), eq(TicketEventType.SLA_BREACHED), eq("system"),
            eq(TicketEvent.ActorKind.SYSTEM), any());
    }

    @Test
    void doesNotRepeatTheEventWhenBreachAlreadyRecorded() {
        watched();
        when(breaches.recordBreach("tk1", "def1", "t1", SlaBreach.BreachType.RESOLUTION, DUE, 100)).thenReturn(false);

        assertThat(service.evaluate("tk1", DUE.plusSeconds(1))).isFalse();
        verify(timeline, never()).append(any(), any(), anyString(), any(), any());
    }

    @Test
    void recordsResponseBreachOnlyWhileThereIsNoFirstResponse() {
        Ticket t = watched();
        t.setFirstResponseAt(null);
        Instant now = t.getResponseDueAt().plusSeconds(1);
        when(breaches.recordBreach("tk1", "def1", "t1", SlaBreach.BreachType.RESPONSE, t.getResponseDueAt(), 100))
            .thenReturn(true);
        when(events.existsByTicketIdAndCycleNoAndEventType("tk1", 1, TicketEventType.SLA_WARNED)).thenReturn(true);

        assertThat(service.evaluate("tk1", now)).isTrue();
    }

    @Test
    void warnsOnceAtEightyPercentOfTheCycle() {
        watched();
        cycleStartedAtStart(0);
        when(events.existsByTicketIdAndCycleNoAndEventType("tk1", 1, TicketEventType.SLA_WARNED)).thenReturn(false);

        assertThat(service.evaluate("tk1", START.plusSeconds(8_100))).isTrue();
        verify(timeline).append(any(), eq(TicketEventType.SLA_WARNED), eq("system"),
            eq(TicketEvent.ActorKind.SYSTEM), any());
    }

    @Test
    void doesNotWarnBeforeEightyPercent() {
        watched();
        cycleStartedAtStart(0);
        when(events.existsByTicketIdAndCycleNoAndEventType("tk1", 1, TicketEventType.SLA_WARNED)).thenReturn(false);

        assertThat(service.evaluate("tk1", START.plusSeconds(7_900))).isFalse();
    }

    @Test
    void pausedTimeDoesNotCountTowardsTheWarning() {
        watched();
        cycleStartedAtStart(2_000);
        when(events.existsByTicketIdAndCycleNoAndEventType("tk1", 1, TicketEventType.SLA_WARNED)).thenReturn(false);

        // 8.100s decorridos menos 2.000s pausados = 6.100s de 8.000s úteis (76%): ainda sem aviso.
        assertThat(service.evaluate("tk1", START.plusSeconds(8_100))).isFalse();
    }

    @Test
    void doesNotWarnTwiceInTheSameCycle() {
        watched();
        when(events.existsByTicketIdAndCycleNoAndEventType("tk1", 1, TicketEventType.SLA_WARNED)).thenReturn(true);

        assertThat(service.evaluate("tk1", START.plusSeconds(9_500))).isFalse();
    }

    @Test
    void backfillsTicketsWithoutSlaAndStopsWhenNoDefinitionApplies() {
        Ticket t = Incident.builder().id("tk1").tenantId("t1").status(Ticket.TicketStatus.OPEN).build();
        when(tickets.findById("tk1")).thenReturn(Optional.of(t));
        when(ticketSla.backfill(t)).thenReturn(false);

        assertThat(service.evaluate("tk1", DUE)).isFalse();
    }
}
