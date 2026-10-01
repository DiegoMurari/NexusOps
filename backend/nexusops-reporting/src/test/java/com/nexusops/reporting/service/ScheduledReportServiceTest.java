package com.nexusops.reporting.service;

import com.nexusops.reporting.domain.ScheduledReport;
import com.nexusops.reporting.dto.CreateScheduledReportRequest;
import com.nexusops.reporting.repository.ScheduledReportRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledReportServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    @Mock
    private ScheduledReportRepository repository;
    @Mock
    private ReportService reportService;

    private ScheduledReportService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledReportService(repository, reportService);
    }

    private CreateScheduledReportRequest request(String cron, String tz) {
        return CreateScheduledReportRequest.builder().reportId("r1").name("Weekly").scheduleCron(cron)
            .timezone(tz).format(ScheduledReport.ExportFormat.CSV)
            .recipients(List.of("a@x.com", "a@x.com", "b@x.com")).build();
    }

    @Test
    void create_computesNextRun_andDedupesRecipients() {
        when(repository.save(any(ScheduledReport.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.create(request("0 0 8 * * MON", "America/Sao_Paulo"), TENANT, USER);

        assertThat(dto.getNextRunAt()).isAfter(Instant.now());
        assertThat(dto.getRecipients()).containsExactly("a@x.com", "b@x.com");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void create_rejectsInvalidCronAndTimezone() {
        assertThatThrownBy(() -> service.create(request("not a cron", "UTC"), TENANT, USER))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(request("0 0 8 * * MON", "Mars/Base"), TENANT, USER))
            .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void list_hidesSchedulesOfReportsNotVisibleToUser() {
        ScheduledReport visible = ScheduledReport.builder().id("s1").reportId("r-visible").tenantId(TENANT).name("A").build();
        ScheduledReport hidden = ScheduledReport.builder().id("s2").reportId("r-private").tenantId(TENANT).name("B").build();
        when(repository.findByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of(visible, hidden));
        when(reportService.listVisible(TENANT, USER)).thenReturn(List.of(
            com.nexusops.reporting.dto.ReportDto.builder().id("r-visible").build()));

        assertThat(service.list(TENANT, USER)).extracting("id").containsExactly("s1");
    }

    @Test
    void delete_isNotFoundWhenReportNotVisible() {
        ScheduledReport hidden = ScheduledReport.builder().id("s2").reportId("r-private").tenantId(TENANT).build();
        when(repository.findByIdAndTenantId("s2", TENANT)).thenReturn(java.util.Optional.of(hidden));
        doThrow(new ResourceNotFoundException("Report", "r-private")).when(reportService)
            .findVisibleOrThrow("r-private", TENANT, USER);

        assertThatThrownBy(() -> service.delete("s2", TENANT, USER)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void create_failsWhenReportNotVisible() {
        doThrow(new ResourceNotFoundException("Report", "r1")).when(reportService)
            .findVisibleOrThrow("r1", TENANT, USER);

        assertThatThrownBy(() -> service.create(request("0 0 8 * * MON", "UTC"), TENANT, USER))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
