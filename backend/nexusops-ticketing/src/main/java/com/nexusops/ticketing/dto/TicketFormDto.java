package com.nexusops.ticketing.dto;

import java.util.Map;

/** O formulário com que um chamado foi aberto: a versão exata da definição e as respostas dadas. */
public record TicketFormDto(
    String versionId,
    int version,
    FormDefinitionDto definition,
    Map<String, Object> answers
) {
}
