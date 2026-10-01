package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Category;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {

    private String id;
    private String name;
    private String description;
    private String tenantId;
    private String parentId;
    private String icon;
    private String color;
    private int sortOrder;
    private boolean active;
    private String slaDefinitionId;
    private Instant createdAt;
    private Instant updatedAt;
}