package com.nexusops.reporting.dto;

import com.nexusops.reporting.domain.ScheduledReport;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReportDto {

    private String id;
    private String reportId;
    private String name;
    private String scheduleCron;
    private String timezone;
    private ScheduledReport.ExportFormat format;
    private ScheduledReport.DeliveryMethod deliveryMethod;
    private List<String> recipients;
    private boolean active;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private String lastRunStatus;
    private Instant createdAt;

    public static ScheduledReportDto from(ScheduledReport s) {
        return ScheduledReportDto.builder()
            .id(s.getId()).reportId(s.getReportId()).name(s.getName())
            .scheduleCron(s.getScheduleCron()).timezone(s.getTimezone())
            .format(s.getFormat()).deliveryMethod(s.getDeliveryMethod())
            .recipients(new ArrayList<>(s.getRecipients()))
            .active(s.isActive()).lastRunAt(s.getLastRunAt()).nextRunAt(s.getNextRunAt())
            .lastRunStatus(s.getLastRunStatus()).createdAt(s.getCreatedAt())
            .build();
    }
}
