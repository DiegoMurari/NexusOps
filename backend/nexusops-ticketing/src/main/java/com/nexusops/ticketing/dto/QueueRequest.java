package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.Size;

/** Criar exige nome e código; no PATCH, nulo mantém e texto vazio limpa a descrição. */
public record QueueRequest(
    @Size(max = 120) String name,
    @Size(max = 40) String code,
    @Size(max = 500) String description,
    Boolean active
) {
}
