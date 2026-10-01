package com.nexusops.sla.mapper;

import com.nexusops.sla.domain.SlaDefinition;
import com.nexusops.sla.dto.SlaDefinitionDto;
import com.nexusops.sla.dto.CreateSlaDefinitionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SlaDefinitionMapper {

    SlaDefinitionMapper INSTANCE = Mappers.getMapper(SlaDefinitionMapper.class);

    SlaDefinitionDto toDto(SlaDefinition slaDefinition);

    SlaDefinition toEntity(CreateSlaDefinitionRequest request);
}