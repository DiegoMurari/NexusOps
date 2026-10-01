package com.nexusops.iam.mapper;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.dto.CreateRoleRequest;
import com.nexusops.iam.dto.UpdateRoleRequest;
import com.nexusops.iam.dto.RoleResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    Role toEntity(CreateRoleRequest request);

    @Mapping(target = "permissions", ignore = true)
    void updateEntity(@MappingTarget Role role, UpdateRoleRequest request);

    RoleResponse toResponse(Role role);
}