package com.nexusops.reporting.dto;

import com.nexusops.reporting.domain.ScheduledReport;
import lombok.*;

import java.time.Instant;
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
}
