package com.nexusops.ticketing.dto;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketStatsDto {

    private Map<String, Long> countsByStatus = new HashMap<>();
    private long assignedToMe;
    private long total;

    public void addCount(String status, long count) {
        countsByStatus.put(status, count);
        total += count;
    }
}