package com.nexusops.reporting.service;

import com.nexusops.reporting.domain.ScheduledReport;
import com.nexusops.reporting.dto.CreateScheduledReportRequest;
import com.nexusops.reporting.dto.ReportDto;
import com.nexusops.reporting.dto.ScheduledReportDto;
import com.nexusops.reporting.dto.UpdateScheduledReportRequest;
import com.nexusops.reporting.repository.ScheduledReportRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages schedule definitions. Automatic execution/delivery is not implemented yet; nextRunAt is
 * computed so the definitions are ready for a future executor.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduledReportService {

    private final ScheduledReportRepository scheduledReportRepository;
    private final ReportService reportService;

    public ScheduledReportDto create(CreateScheduledReportRequest request, String tenantId, String userId) {
        reportService.findVisibleOrThrow(request.getReportId(), tenantId, userId);
        String zone = request.getTimezone() == null || request.getTimezone().isBlank() ? "UTC" : request.getTimezone();
        String cron = request.getScheduleCron().trim();

        ScheduledReport scheduled = ScheduledReport.builder()
            .reportId(request.getReportId())
            .tenantId(tenantId)
            .name(request.getName().trim())
            .scheduleCron(cron)
            .timezone(zone)
            .format(request.getFormat())
            .recipients(new ArrayList<>(new LinkedHashSet<>(request.getRecipients())))
            .nextRunAt(computeNextRun(cron, zone))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        return ScheduledReportDto.from(scheduledReportRepository.save(scheduled));
    }

    @Transactional(readOnly = true)
    public List<ScheduledReportDto> list(String tenantId, String userId) {
        Set<String> visibleReportIds = reportService.listVisible(tenantId, userId).stream()
            .map(ReportDto::getId).collect(Collectors.toSet());
        return scheduledReportRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .filter(s -> visibleReportIds.contains(s.getReportId()))
            .map(ScheduledReportDto::from).toList();
    }

    public ScheduledReportDto update(String id, String tenantId, String userId, UpdateScheduledReportRequest request) {
        ScheduledReport scheduled = find(id, tenantId, userId);
        if (request.getActive() != null) {
            scheduled.setActive(request.getActive());
            scheduled.setNextRunAt(request.getActive()
                ? computeNextRun(scheduled.getScheduleCron(), scheduled.getTimezone()) : null);
        }
        scheduled.setUpdatedBy(userId);
        return ScheduledReportDto.from(scheduledReportRepository.save(scheduled));
    }

    public void delete(String id, String tenantId, String userId) {
        scheduledReportRepository.delete(find(id, tenantId, userId));
    }

    /** Resolves a schedule only when its underlying report is visible to the user. */
    private ScheduledReport find(String id, String tenantId, String userId) {
        ScheduledReport scheduled = scheduledReportRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("ScheduledReport", id));
        try {
            reportService.findVisibleOrThrow(scheduled.getReportId(), tenantId, userId);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("ScheduledReport", id);
        }
        return scheduled;
    }

    static Instant computeNextRun(String cron, String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ValidationException("Invalid timezone: " + timezone);
        }
        CronExpression expression;
        try {
            expression = CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid cron expression (expected 6 fields: sec min hour day month weekday)");
        }
        ZonedDateTime next = expression.next(ZonedDateTime.now(zone));
        return next == null ? null : next.toInstant();
    }
}
