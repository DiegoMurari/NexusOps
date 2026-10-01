package com.nexusops.ticketing.dto;

import com.nexusops.ticketing.domain.Ticket;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDto {

    private String id;
    private String ticketNumber;
    private String title;
    private String description;
    private Ticket.TicketStatus status;
    private Ticket.Priority priority;
    private Ticket.Urgency urgency;
    private Ticket.Impact impact;
    private String tenantId;
    private String categoryId;
    private String assigneeId;
    private String reporterId;
    private String groupId;
    private String slaDefinitionId;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Instant firstResponseAt;
    private Instant resolvedAt;
    private Instant closedAt;
    private String ciReference;
    private String tags;
    private String customFields;
    private Instant createdAt;
    private Instant updatedAt;
    private String ticketType;
}