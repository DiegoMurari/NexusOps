package com.nexusops.ticketing.mapper;

import com.nexusops.ticketing.domain.Change;
import com.nexusops.ticketing.domain.Problem;
import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.TicketDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    TicketMapper INSTANCE = Mappers.getMapper(TicketMapper.class);

    @Mapping(target = "ticketType", expression = "java(ticketTypeOf(ticket))")
    TicketDto toDto(Ticket ticket);

    /** O tipo é a coluna discriminadora da herança JOINED, não uma propriedade da entidade. */
    default String ticketTypeOf(Ticket ticket) {
        if (ticket instanceof Problem) {
            return "PROBLEM";
        }
        if (ticket instanceof Change) {
            return "CHANGE";
        }
        return "INCIDENT";
    }
}