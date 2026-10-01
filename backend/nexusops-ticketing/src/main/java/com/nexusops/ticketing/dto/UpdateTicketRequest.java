package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Ticket;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTicketRequest {

    @Size(max = 500)
    private String title;

    @Size(max = 10000)
    private String description;

    private Ticket.Priority priority;

    private Ticket.Urgency urgency;

    private Ticket.Impact impact;

    private String categoryId;

    private String assigneeId;

    private String groupId;

    private String ciReference;

    private String tags;

    private String customFields;
}