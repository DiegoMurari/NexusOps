package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    @Mapping(target = "assignedToName", ignore = true)
    @Mapping(target = "locationName", ignore = true)
    AssetDto toDto(Asset asset);

    Asset toEntity(CreateAssetRequest request);
}
