package com.nexusops.reporting.service;

import com.nexusops.reporting.mapper.ReportMapper;
import com.nexusops.reporting.domain.Report;
import com.nexusops.reporting.dto.*;
import com.nexusops.reporting.repository.ReportRepository;
import com.nexusops.reporting.repository.ScheduledReportRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final ScheduledReportRepository scheduledReportRepository;
    private final ReportDataService dataService;

    public ReportDto create(CreateReportRequest request, String tenantId, String userId) {
        Report report = Report.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .tenantId(tenantId)
            .ownerId(userId)
            .reportType(request.getReportType())
            .publicReport(Boolean.TRUE.equals(request.getPublicReport()))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        return ReportMapper.toDto(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listVisible(String tenantId, String userId) {
        return reportRepository.findVisible(tenantId, userId).stream().map(ReportMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReportDto get(String id, String tenantId, String userId) {
        return ReportMapper.toDto(findVisibleOrThrow(id, tenantId, userId));
    }

    public ReportDto update(String id, String tenantId, String userId, UpdateReportRequest request) {
        Report report = findOwnedOrThrow(id, tenantId, userId);
        if (request.getName() != null) report.setName(request.getName().trim());
        if (request.getDescription() != null) report.setDescription(request.getDescription());
        if (request.getPublicReport() != null) report.setPublicReport(request.getPublicReport());
        report.setUpdatedBy(userId);
        return ReportMapper.toDto(reportRepository.save(report));
    }

    public void delete(String id, String tenantId, String userId) {
        Report report = findOwnedOrThrow(id, tenantId, userId);
        scheduledReportRepository.deleteByReportIdAndTenantId(id, tenantId);
        reportRepository.delete(report);
    }

    @Transactional(readOnly = true)
    public ReportResultDto run(String id, String tenantId, String userId, int days) {
        Report report = findVisibleOrThrow(id, tenantId, userId);
        return dataService.run(report.getReportType(), tenantId, days);
    }

    /** Roda uma análise nativa direto, sem precisar de um relatório salvo (painel analítico). */
    @Transactional(readOnly = true)
    public ReportResultDto runBuiltin(Report.ReportType type, String tenantId, int days) {
        return dataService.run(type, tenantId, days);
    }

    @Transactional(readOnly = true)
    public ReportResultDto overview(String tenantId, int days) {
        return dataService.run(Report.ReportType.TICKET_SUMMARY, tenantId, days);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String id, String tenantId, String userId, int days) {
        ReportResultDto result = run(id, tenantId, userId, days);
        StringBuilder sb = new StringBuilder();
        sb.append(result.getColumns().stream().map(ReportService::csvCell).collect(Collectors.joining(","))).append('\n');
        for (Map<String, Object> row : result.getRows()) {
            sb.append(result.getColumns().stream().map(c -> csvCell(String.valueOf(row.get(c))))
                .collect(Collectors.joining(","))).append('\n');
        }
        return sb.toString();
    }

    /** Quotes the cell and neutralizes spreadsheet formula injection (leading =, +, -, @, tab, CR). */
    static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    Report findVisibleOrThrow(String id, String tenantId, String userId) {
        Report report = reportRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Report", id));
        if (!report.isPublicReport() && !report.getOwnerId().equals(userId)) {
            throw new ResourceNotFoundException("Report", id);
        }
        return report;
    }

    private Report findOwnedOrThrow(String id, String tenantId, String userId) {
        Report report = reportRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Report", id));
        if (!report.getOwnerId().equals(userId)) {
            throw new ResourceNotFoundException("Report", id);
        }
        return report;
    }
}
