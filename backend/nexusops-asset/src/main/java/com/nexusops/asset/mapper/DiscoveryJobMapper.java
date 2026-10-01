package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.DiscoveryJob;
import com.nexusops.asset.dto.CreateDiscoveryJobRequest;
import com.nexusops.asset.dto.DiscoveryJobDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DiscoveryJobMapper {

    DiscoveryJobDto toDto(DiscoveryJob job);

    DiscoveryJob toEntity(CreateDiscoveryJobRequest request);
}
