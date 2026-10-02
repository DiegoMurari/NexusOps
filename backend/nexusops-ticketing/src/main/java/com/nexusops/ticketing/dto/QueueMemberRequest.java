package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotBlank;

/** Adiciona o usuário à fila ou muda o papel dele (MEMBER ou LEAD). */
public record QueueMemberRequest(
    @NotBlank String userId,
    String role
) {
}
