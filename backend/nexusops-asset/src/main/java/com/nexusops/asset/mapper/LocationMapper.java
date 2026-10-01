package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.Location;
import com.nexusops.asset.dto.CreateLocationRequest;
import com.nexusops.asset.dto.LocationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationDto toDto(Location location);

    Location toEntity(CreateLocationRequest request);
}
