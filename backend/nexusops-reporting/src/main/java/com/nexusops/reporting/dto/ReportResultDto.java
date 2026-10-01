package com.nexusops.reporting.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResultDto {

    private String reportType;
    private Instant from;
    private Instant to;
    private Instant generatedAt;
    private Map<String, Object> summary;
    private List<String> columns;
    private List<Map<String, Object>> rows;
}
