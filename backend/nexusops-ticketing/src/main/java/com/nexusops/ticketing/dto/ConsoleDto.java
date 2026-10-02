package com.nexusops.ticketing.dto;

import java.util.List;

/** Leituras do Console do analista (ADR-013, Fase H): o contexto de um chamado para quem o atende. */
public final class ConsoleDto {

    private ConsoleDto() {
    }

    /**
     * De onde o chamado veio e como chegou aqui: fila, tópico, área, quem pediu, quem atende e as respostas do
     * formulário com rótulo. Nomes já resolvidos, para a tela não precisar de permissão de administração.
     */
    public record Context(
        String queueId,
        String queueName,
        String topicName,
        String areaName,
        String reporterName,
        String assigneeName,
        List<PortalDto.Answer> answers
    ) {
    }

    /** Contagens da operação do chamador, para a faixa de leituras e as abas da lista. */
    public record Counts(long mine, long myQueues, long unassigned, long all) {
    }
}
