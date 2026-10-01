package com.nexusops.reporting.dto;

import com.nexusops.reporting.domain.ScheduledReport;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateScheduledReportRequest {

    @NotBlank
    private String reportId;

    @NotBlank
    @Size(max = 255)
    private String name;

    /** Spring 6-field cron expression (sec min hour day month weekday). */
    @NotBlank
    @Size(max = 100)
    private String scheduleCron;

    @Size(max = 50)
    private String timezone;

    @NotNull
    private ScheduledReport.ExportFormat format;

    @NotEmpty
    @Size(max = 20)
    private List<@Email @Size(max = 255) String> recipients;
}
