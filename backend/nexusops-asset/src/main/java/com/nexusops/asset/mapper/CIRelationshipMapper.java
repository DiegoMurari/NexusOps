package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.CIRelationship;
import com.nexusops.asset.dto.CIRelationshipDto;
import com.nexusops.asset.dto.CreateCIRelationshipRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CIRelationshipMapper {

    CIRelationshipDto toDto(CIRelationship relationship);

    CIRelationship toEntity(CreateCIRelationshipRequest request);
}
