package com.nexusops.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Um ticket vinculado a um artigo, já com número, título e situação para exibição. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkedTicketDto {

    private String ticketId;
    private String ticketNumber;
    private String title;
    private String status;
    private String linkedBy;
    private Instant linkedAt;
}
