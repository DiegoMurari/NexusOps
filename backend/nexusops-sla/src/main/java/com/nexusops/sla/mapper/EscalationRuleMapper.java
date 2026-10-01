package com.nexusops.sla.mapper;

import com.nexusops.sla.domain.EscalationRule;
import com.nexusops.sla.dto.EscalationRuleDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface EscalationRuleMapper {

    EscalationRuleMapper INSTANCE = Mappers.getMapper(EscalationRuleMapper.class);

    EscalationRuleDto toDto(EscalationRule rule);
}