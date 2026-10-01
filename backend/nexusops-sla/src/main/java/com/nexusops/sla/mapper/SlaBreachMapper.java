package com.nexusops.sla.mapper;

import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.dto.SlaBreachDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SlaBreachMapper {

    SlaBreachMapper INSTANCE = Mappers.getMapper(SlaBreachMapper.class);

    SlaBreachDto toDto(SlaBreach breach);
}