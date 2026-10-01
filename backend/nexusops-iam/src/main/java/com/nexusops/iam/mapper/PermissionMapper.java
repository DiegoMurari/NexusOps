package com.nexusops.iam.mapper;

import com.nexusops.iam.domain.Permission;
import com.nexusops.iam.dto.PermissionDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionMapper INSTANCE = Mappers.getMapper(PermissionMapper.class);

    PermissionDto toDto(Permission permission);
}