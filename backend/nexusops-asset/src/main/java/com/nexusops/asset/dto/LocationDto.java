package com.nexusops.asset.dto;

import com.nexusops.asset.domain.Location;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationDto {

    private String id;
    private String name;
    private String code;
    private Boolean active;
    private String description;
    private String tenantId;
    private String parentId;
    private Location.LocationType type;
    private String address;
    private String coordinates;
    private Instant createdAt;
    private Instant updatedAt;

    /** O mínimo para escolher uma localidade em um formulário: sem endereço nem coordenadas. */
    public record Option(String id, String name, String code) {
    }
}
