package com.nexusops.reporting.service;

import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.ReportResultDto;
import com.nexusops.shared.directory.UserDirectory;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.ticketing.domain.Incident;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.repository.CategoryRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDataServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserDirectory userDirectory;

    private ReportDataService service;

    @BeforeEach
    void setUp() {
        service = new ReportDataService(ticketRepository, categoryRepository, userDirectory);
    }

    private Ticket ticket(Ticket.TicketStatus status, Instant created, Instant resolved, Instant due) {
        return Incident.builder()
            .id("t-" + System.nanoTime())
            .tenantId(TENANT)
            .reporterId("u1")
            .title("x")
            .status(status)
            .createdAt(created)
            .resolvedAt(resolved)
            .resolutionDueAt(due)
            .build();
    }

    private void givenTickets(Ticket... tickets) {
        when(ticketRepository.findByTenantIdAndCreatedAtBetween(eq(TENANT), any(), any()))
            .thenReturn(List.of(tickets));
    }

    @Test
    void ticketSummary_countsOpenResolvedAndAverageHours() {
        Instant now = Instant.now();
        givenTickets(
            ticket(Ticket.TicketStatus.OPEN, now.minus(5, ChronoUnit.HOURS), null, null),
            ticket(Ticket.TicketStatus.RESOLVED, now.minus(10, ChronoUnit.HOURS), now.minus(8, ChronoUnit.HOURS), null));

        ReportResultDto result = service.run(Report.ReportType.TICKET_SUMMARY, TENANT, 30);

        assertThat(result.getSummary()).containsEntry("total", 2).containsEntry("open", 1L)
            .containsEntry("resolved", 1L).containsEntry("avgResolutionHours", 2.0);
    }

    @Test
    void ticketSummary_treatsOnHoldAndReopenedAsStillOpen() {
        Instant now = Instant.now();
        givenTickets(
            ticket(Ticket.TicketStatus.ON_HOLD, now.minus(5, ChronoUnit.HOURS), null, null),
            ticket(Ticket.TicketStatus.REOPENED, now.minus(5, ChronoUnit.HOURS), null, null),
            ticket(Ticket.TicketStatus.CLOSED, now.minus(5, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), null));

        ReportResultDto result = service.run(Report.ReportType.TICKET_SUMMARY, TENANT, 30);

        assertThat(result.getSummary()).containsEntry("open", 2L).containsEntry("resolved", 1L);
    }

    @Test
    void slaCompliance_classifiesMetBreachedAndPending() {
        Instant now = Instant.now();
        givenTickets(
            ticket(Ticket.TicketStatus.RESOLVED, now.minus(10, ChronoUnit.HOURS), now.minus(6, ChronoUnit.HOURS), now.minus(5, ChronoUnit.HOURS)),
            ticket(Ticket.TicketStatus.RESOLVED, now.minus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), now.minus(5, ChronoUnit.HOURS)),
            ticket(Ticket.TicketStatus.OPEN, now.minus(2, ChronoUnit.HOURS), null, now.plus(5, ChronoUnit.HOURS)),
            ticket(Ticket.TicketStatus.OPEN, now.minus(2, ChronoUnit.HOURS), null, null));

        ReportResultDto result = service.run(Report.ReportType.SLA_COMPLIANCE, TENANT, 30);

        assertThat(result.getSummary()).containsEntry("met", 1L).containsEntry("breached", 1L)
            .containsEntry("pending", 1L).containsEntry("compliancePercentage", 50.0);
    }

    @Test
    void categoryDistribution_groupsUncategorizedTickets() {
        givenTickets(ticket(Ticket.TicketStatus.OPEN, Instant.now().minusSeconds(60), null, null));
        when(categoryRepository.findByTenantId(TENANT)).thenReturn(List.of());

        ReportResultDto result = service.run(Report.ReportType.CATEGORY_DISTRIBUTION, TENANT, 7);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0)).containsEntry("category", "Sem categoria").containsEntry("count", 1L);
    }

    @Test
    void backlog_bucketsOpenTicketsByAgeAndCountsOverdueAndUnassigned() {
        Instant now = Instant.now();
        Ticket fresh = ticket(Ticket.TicketStatus.OPEN, now.minus(2, ChronoUnit.HOURS), null, now.plus(3, ChronoUnit.HOURS));
        Ticket twoDays = ticket(Ticket.TicketStatus.IN_PROGRESS, now.minus(48, ChronoUnit.HOURS), null, now.minus(1, ChronoUnit.HOURS));
        twoDays.setAssigneeId("ana@x.com");
        Ticket old = ticket(Ticket.TicketStatus.REOPENED, now.minus(40, ChronoUnit.DAYS), null, null);
        when(ticketRepository.findByTenantIdAndStatusIn(eq(TENANT), any())).thenReturn(List.of(fresh, twoDays, old));

        ReportResultDto result = service.run(Report.ReportType.BACKLOG, TENANT, 30);

        assertThat(result.getSummary()).containsEntry("open", 3).containsEntry("unassigned", 2L)
            .containsEntry("overdue", 1L).containsEntry("oldestDays", 40L);
        assertThat(result.getRows()).extracting(r -> r.get("age"), r -> r.get("count"))
            .containsExactly(
                tuple("Até 1 dia", 1L),
                tuple("1 a 3 dias", 1L),
                tuple("3 a 7 dias", 0L),
                tuple("7 a 30 dias", 0L),
                tuple("Mais de 30 dias", 1L));
    }

    @Test
    void backlog_withNothingOpenReportsZeroPercentages() {
        when(ticketRepository.findByTenantIdAndStatusIn(eq(TENANT), any())).thenReturn(List.of());

        ReportResultDto result = service.run(Report.ReportType.BACKLOG, TENANT, 7);

        assertThat(result.getSummary()).containsEntry("open", 0);
        assertThat(result.getRows()).extracting(r -> r.get("percentage")).containsOnly(0.0);
    }

    @Test
    void trend_includesEveryDayOfThePeriod_evenWithoutActivity() {
        Instant now = Instant.now();
        givenTickets(ticket(Ticket.TicketStatus.OPEN, now.minus(1, ChronoUnit.HOURS), null, null));

        ReportResultDto result = service.run(Report.ReportType.TREND_ANALYSIS, TENANT, 7);

        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        assertThat(result.getRows()).hasSize(8);
        assertThat(result.getRows().get(0)).containsEntry("date", today.minusDays(7).toString()).containsEntry("created", 0L);
        assertThat(result.getRows().get(7)).containsEntry("date", today.toString());
    }

    @Test
    void agentPerformance_showsTheNameResolvedFromTheEmail_orFallsBackToIt() {
        Instant now = Instant.now();
        Ticket a = ticket(Ticket.TicketStatus.RESOLVED, now.minus(5, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS), null);
        a.setAssigneeId("ana@x.com");
        Ticket b = ticket(Ticket.TicketStatus.OPEN, now.minus(5, ChronoUnit.HOURS), null, null);
        b.setAssigneeId("ghost@x.com");
        givenTickets(a, b);
        when(userDirectory.findActiveByEmail("ana@x.com", TENANT)).thenReturn(Optional.of(
            new UserDirectory.UserRef("u1", "Ana Souza", "ana@x.com", true)));
        when(userDirectory.findActiveByEmail("ghost@x.com", TENANT)).thenReturn(Optional.empty());
        when(userDirectory.findActive("ghost@x.com", TENANT)).thenReturn(Optional.empty());

        ReportResultDto result = service.run(Report.ReportType.AGENT_PERFORMANCE, TENANT, 30);

        assertThat(result.getRows()).extracting(r -> r.get("agent")).containsExactlyInAnyOrder("Ana Souza", "ghost@x.com");
    }

    @Test
    void run_rejectsCustomTypeAndOutOfRangeDays() {
        assertThatThrownBy(() -> service.run(Report.ReportType.TICKET_SUMMARY, TENANT, 0))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.run(Report.ReportType.TICKET_SUMMARY, TENANT, 366))
            .isInstanceOf(ValidationException.class);

        when(ticketRepository.findByTenantIdAndCreatedAtBetween(eq(TENANT), any(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.run(Report.ReportType.CUSTOM, TENANT, 30))
            .isInstanceOf(ValidationException.class);
    }
}
