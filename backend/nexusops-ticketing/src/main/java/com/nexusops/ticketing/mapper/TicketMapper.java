package com.nexusops.ticketing.mapper;

import com.nexusops.ticketing.domain.Ticket;
import com.nexusops.ticketing.dto.TicketDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    TicketMapper INSTANCE = Mappers.getMapper(TicketMapper.class);

    TicketDto toDto(Ticket ticket);
}