package com.nexusops.ticketing.mapper;

import com.nexusops.ticketing.domain.TimeEntry;
import com.nexusops.ticketing.dto.TimeEntryDto;
import com.nexusops.ticketing.dto.CreateTimeEntryRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TimeEntryMapper {

    TimeEntryMapper INSTANCE = Mappers.getMapper(TimeEntryMapper.class);

    TimeEntryDto toDto(TimeEntry timeEntry);

    TimeEntry toEntity(CreateTimeEntryRequest request);
}