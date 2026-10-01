package com.nexusops.reporting.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReportRequest {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private Boolean publicReport;
}
