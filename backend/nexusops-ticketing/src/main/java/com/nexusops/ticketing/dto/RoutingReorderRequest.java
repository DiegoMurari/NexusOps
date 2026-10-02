package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Nova ordem de avaliação: todos os IDs de regra do tenant, na ordem desejada. */
public record RoutingReorderRequest(@NotNull List<String> ids) {
}
