package com.nexusops.reporting.service;

import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.ReportResultDto;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private UserRepository userRepository;

    private ReportDataService service;

    @BeforeEach
    void setUp() {
        service = new ReportDataService(ticketRepository, categoryRepository, userRepository);
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
