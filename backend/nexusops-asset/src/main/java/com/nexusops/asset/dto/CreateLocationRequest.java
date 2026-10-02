package com.nexusops.asset.dto;

import com.nexusops.asset.domain.Location;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /** Código estável da localidade (letras, números, hífen e sublinhado). Opcional. */
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "code may only contain letters, digits, '-' and '_'")
    private String code;

    /** Nulo mantém o valor atual; falso aposenta a localidade sem apagá-la. */
    private Boolean active;

    @Size(max = 1000)
    private String description;

    private String parentId;

    private Location.LocationType type;

    private String address;

    @Size(max = 50)
    private String coordinates;
}
