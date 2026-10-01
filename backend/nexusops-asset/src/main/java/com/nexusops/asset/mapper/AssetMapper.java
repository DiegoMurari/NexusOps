package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    AssetDto toDto(Asset asset);

    Asset toEntity(CreateAssetRequest request);
}
