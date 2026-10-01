package com.nexusops.knowledge.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCategoryRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private String parentId;

    @Size(max = 100)
    private String icon;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex value like #1A2B3C")
    private String color;

    private Integer sortOrder;

    private Boolean active;
}
