package com.nexusops.platform.mapper;

import com.nexusops.platform.domain.FeatureFlag;
import com.nexusops.platform.dto.FeatureFlagDto;
import com.nexusops.platform.dto.CreateFeatureFlagRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface FeatureFlagMapper {

    FeatureFlagMapper INSTANCE = Mappers.getMapper(FeatureFlagMapper.class);

    FeatureFlagDto toDto(FeatureFlag featureFlag);

    FeatureFlag toEntity(CreateFeatureFlagRequest request);
}