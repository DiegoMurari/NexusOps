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