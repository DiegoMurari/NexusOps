package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignRequest {

    @NotNull
    private String assigneeId;

    private String groupId;
}