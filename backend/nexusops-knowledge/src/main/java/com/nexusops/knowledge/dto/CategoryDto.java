package com.nexusops.knowledge.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String tenantId;
    private String parentId;
    private String icon;
    private String color;
    private int sortOrder;
    private boolean active;
    private int articleCount;
    private Instant createdAt;
    private Instant updatedAt;
}
