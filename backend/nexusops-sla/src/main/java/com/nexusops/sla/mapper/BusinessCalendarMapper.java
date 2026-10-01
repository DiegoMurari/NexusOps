package com.nexusops.sla.mapper;

import com.nexusops.sla.domain.BusinessCalendar;
import com.nexusops.sla.dto.BusinessCalendarDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface BusinessCalendarMapper {

    BusinessCalendarMapper INSTANCE = Mappers.getMapper(BusinessCalendarMapper.class);

    BusinessCalendarDto toDto(BusinessCalendar calendar);
}