package com.nexusops.reporting.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateScheduledReportRequest {

    private Boolean active;
}
