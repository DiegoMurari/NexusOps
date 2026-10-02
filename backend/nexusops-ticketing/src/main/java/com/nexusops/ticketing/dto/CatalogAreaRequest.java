package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.Size;

public record CatalogAreaRequest(
    @Size(max = 120) String name,
    @Size(max = 500) String description,
    @Size(max = 60) String icon,
    Integer sortOrder,
    Boolean active
) {
}
