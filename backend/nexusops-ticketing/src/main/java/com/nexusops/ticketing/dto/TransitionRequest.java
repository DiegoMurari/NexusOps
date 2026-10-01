package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Ticket;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitionRequest {

    @NotNull
    private Ticket.TicketStatus targetStatus;

    private String comment;

    private String resolution;
}