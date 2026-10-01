package com.nexusops.platform.mapper;

import com.nexusops.platform.domain.Tenant;
import com.nexusops.platform.dto.TenantDto;
import com.nexusops.platform.dto.CreateTenantRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantMapper INSTANCE = Mappers.getMapper(TenantMapper.class);

    TenantDto toDto(Tenant tenant);

    Tenant toEntity(CreateTenantRequest request);
}