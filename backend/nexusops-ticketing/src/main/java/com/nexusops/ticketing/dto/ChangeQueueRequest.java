package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.Size;

/** Move o chamado para outra fila (nulo/vazio = sem fila). O responsável individual não muda. */
public record ChangeQueueRequest(String queueId, @Size(max = 500) String reason) {
}
