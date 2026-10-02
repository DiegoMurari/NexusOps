package com.nexusops.iam.mapper;

import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.dto.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    /** A localidade padrão é validada e aplicada pelo UserService, não copiada às cegas. */
    @Mapping(target = "defaultLocationId", ignore = true)
    User toEntity(CreateUserRequest request);

    /** PATCH: campo nulo no pedido mantém o valor atual (não apaga). */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "defaultLocationId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "mfaEnabled", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    void updateEntity(@MappingTarget User user, UpdateUserRequest request);

    UserResponse toResponse(User user);
}