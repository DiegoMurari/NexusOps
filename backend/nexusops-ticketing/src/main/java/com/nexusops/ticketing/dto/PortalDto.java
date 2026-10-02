package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * O que o Portal mostra ao solicitante (ADR-013). Nunca carrega fila, prioridade, SLA, regras de roteamento nem
 * eventos internos: o solicitante vê o andamento do próprio pedido, não a estrutura da operação.
 */
public final class PortalDto {

    private PortalDto() {
    }

    /**
     * Etapa na visão do solicitante: RECEIVED, IN_PROGRESS, WAITING_YOU (a equipe pediu informação),
     * AWAITING_VALIDATION (há uma solução para ele aceitar ou contestar) ou CLOSED.
     */
    public record Ticket(
        String id,
        String ticketNumber,
        String title,
        String stage,
        boolean needsAttention,
        String areaName,
        String topicName,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    /** Um evento do histórico visível ao solicitante. {@code actor}: REQUESTER (ele mesmo), TEAM ou SYSTEM. */
    public record Event(String id, String type, String text, String actor, int cycleNo, Instant occurredAt) {
    }

    public record Answer(String label, String value) {
    }

    public record Detail(
        Ticket ticket,
        String description,
        List<Answer> answers,
        /** A solução apresentada, enquanto aguarda a validação do solicitante. */
        String pendingSolution,
        boolean canReply,
        List<Event> timeline,
        List<EvidenceDto> evidence
    ) {
    }

    public record CreateRequest(
        @NotBlank String topicId,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 10000) String description,
        String locationId,
        Map<String, Object> formAnswers,
        List<String> evidenceIds
    ) {
    }

    public record MessageRequest(@NotBlank @Size(max = 5000) String message) {
    }

    /** Aceitar aceita comentário opcional; contestar exige o motivo. */
    public record DecisionRequest(@Size(max = 5000) String comment) {
    }
}
