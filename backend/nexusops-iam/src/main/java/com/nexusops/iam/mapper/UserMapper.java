package com.nexusops.iam.mapper;

import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.dto.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    User toEntity(CreateUserRequest request);

    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "mfaEnabled", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    void updateEntity(@MappingTarget User user, UpdateUserRequest request);

    UserResponse toResponse(User user);
}