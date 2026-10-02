package com.nexusops.reporting.dto;

import com.nexusops.reporting.domain.Report;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDto {

    private String id;
    private String name;
    private String description;
    private String ownerId;
    private Report.ReportType reportType;
    private boolean publicReport;
    private Instant createdAt;
    private Instant updatedAt;
}
