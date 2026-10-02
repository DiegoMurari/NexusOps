package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    @Size(max = 10000)
    private String description;

    @NotNull
    private Ticket.Priority priority = Ticket.Priority.MEDIUM;

    private Ticket.Urgency urgency;

    private Ticket.Impact impact;

    @NotNull
    private String tenantId;

    private String categoryId;

    private String assigneeId;

    @NotNull
    private String reporterId;

    private String groupId;

    /** Tópico do catálogo: quando informado, o sistema deriva fila, prioridade inicial e SLA dele. */
    private String topicId;

    /** Fila escolhida pelo atendente (ignorada quando o tópico define a fila). */
    private String queueId;

    private String locationId;

    /** Respostas do formulário do tópico; o servidor valida contra a versão publicada. */
    private java.util.Map<String, Object> formAnswers;

    /** IDs de evidências já enviadas (preparadas) para este chamado; a política do formulário diz se são exigidas. */
    private java.util.List<String> evidenceIds;

    private String ciReference;

    private String tags;

    private String customFields;

    @NotNull
    private TicketType ticketType = TicketType.INCIDENT;

    public enum TicketType {
        INCIDENT,
        PROBLEM,
        CHANGE
    }
}