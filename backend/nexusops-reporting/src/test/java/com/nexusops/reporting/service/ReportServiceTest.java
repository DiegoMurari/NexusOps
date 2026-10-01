package com.nexusops.reporting.service;

import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.CreateReportRequest;
import com.nexusops.reporting.dto.ReportDto;
import com.nexusops.reporting.dto.UpdateReportRequest;
import com.nexusops.reporting.repository.ReportRepository;
import com.nexusops.reporting.repository.ScheduledReportRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String OWNER = "owner-1";
    private static final String OTHER = "other-1";

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ScheduledReportRepository scheduledReportRepository;
    @Mock
    private ReportDataService dataService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reportRepository, scheduledReportRepository, dataService);
    }

    private Report privateReport() {
        return Report.builder().id("r1").name("R").tenantId(TENANT).ownerId(OWNER)
            .reportType(Report.ReportType.TICKET_SUMMARY).build();
    }

    @Test
    void create_setsTenantAndOwnerFromArguments_andDefaultsToPrivate() {
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportDto dto = service.create(CreateReportRequest.builder().name("  Weekly ")
            .reportType(Report.ReportType.TREND_ANALYSIS).build(), TENANT, OWNER);

        assertThat(dto.getName()).isEqualTo("Weekly");
        assertThat(dto.getOwnerId()).isEqualTo(OWNER);
        assertThat(dto.isPublicReport()).isFalse();
    }

    @Test
    void get_hidesPrivateReportFromOtherUsers() {
        when(reportRepository.findByIdAndTenantId("r1", TENANT)).thenReturn(Optional.of(privateReport()));

        assertThatThrownBy(() -> service.get("r1", TENANT, OTHER)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.get("r1", TENANT, OWNER).getId()).isEqualTo("r1");
    }

    @Test
    void update_isOwnerOnly_evenForPublicReports() {
        Report report = privateReport();
        report.setPublicReport(true);
        when(reportRepository.findByIdAndTenantId("r1", TENANT)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.update("r1", TENANT, OTHER, UpdateReportRequest.builder().name("x").build()))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void delete_removesSchedulesThenReport() {
        Report report = privateReport();
        when(reportRepository.findByIdAndTenantId("r1", TENANT)).thenReturn(Optional.of(report));

        service.delete("r1", TENANT, OWNER);

        verify(scheduledReportRepository).deleteByReportIdAndTenantId("r1", TENANT);
        verify(reportRepository).delete(report);
    }

    @Test
    void csvCell_quotesAndNeutralizesFormulaInjection() {
        assertThat(ReportService.csvCell("=SUM(A1)")).isEqualTo("\"'=SUM(A1)\"");
        assertThat(ReportService.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.csvCell("plain")).isEqualTo("\"plain\"");
    }
}
