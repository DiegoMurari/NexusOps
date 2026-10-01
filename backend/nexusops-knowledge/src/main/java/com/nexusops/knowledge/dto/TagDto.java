package com.nexusops.knowledge.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagDto {

    private String id;
    private String name;
    private String tenantId;
    private String description;
    private int usageCount;
}
