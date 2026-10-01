package com.nexusops.platform.mapper;

import com.nexusops.platform.domain.SystemSetting;
import com.nexusops.platform.dto.SystemSettingDto;
import com.nexusops.platform.dto.CreateSystemSettingRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SystemSettingMapper {

    SystemSettingMapper INSTANCE = Mappers.getMapper(SystemSettingMapper.class);

    SystemSettingDto toDto(SystemSetting systemSetting);

    SystemSetting toEntity(CreateSystemSettingRequest request);
}