package com.nexusops.asset.dto;

import com.nexusops.asset.domain.Location;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLocationRequest {

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private String parentId;

    private Location.LocationType type;

    private String address;

    @Size(max = 50)
    private String coordinates;
}
