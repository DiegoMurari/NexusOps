package com.nexusops.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.domain.TicketEvent;
import com.nexusops.ticketing.domain.TicketEventType;
import com.nexusops.ticketing.dto.TicketEventDto;
import com.nexusops.ticketing.repository.TicketEventRepository;
import com.nexusops.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Timeline append-only do chamado. Só acrescenta: não existe operação de alterar ou remover evento
 * (e o banco também rejeita UPDATE e DELETE).
 */
@Service
@RequiredArgsConstructor
public class TicketTimelineService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final TicketEventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper;

    /** Acrescenta um evento na mesma transação da mudança que ele descreve. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketEvent append(Ticket ticket, TicketEventType type, String actorId,
                              TicketEvent.ActorKind actorKind, Map<String, Object> payload) {
        return append(ticket, type, actorId, actorKind, type.defaultVisibility(), payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TicketEvent append(Ticket ticket, TicketEventType type, String actorId,
                              TicketEvent.ActorKind actorKind, TicketEvent.Visibility visibility,
                              Map<String, Object> payload) {
        TicketEvent event = TicketEvent.newEvent()
            .ticketId(ticket.getId())
            .tenantId(ticket.getTenantId())
            .seq(eventRepository.nextSeq(ticket.getId()))
            .cycleNo(ticket.getCycleNo())
            .eventType(type)
            .actorId(actorId)
            .actorKind(actorKind)
            .visibility(visibility)
            .payload(toJson(payload))
            .build();
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<TicketEventDto> timeline(String ticketId, boolean includeInternal) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket", ticketId);
        }
        List<TicketEvent> events = includeInternal
            ? eventRepository.findByTicketIdOrderBySeqAsc(ticketId)
            : eventRepository.findByTicketIdAndVisibilityOrderBySeqAsc(ticketId, TicketEvent.Visibility.PUBLIC);
        return events.stream().map(this::toDto).toList();
    }

    private TicketEventDto toDto(TicketEvent e) {
        return new TicketEventDto(e.getId(), e.getSeq(), e.getCycleNo(), e.getEventType().name(),
            e.getActorId(), e.getActorKind().name(), e.getVisibility().name(), fromJson(e.getPayload()),
            e.getOccurredAt());
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize ticket event payload", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read ticket event payload", ex);
        }
    }
}
