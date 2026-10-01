package com.nexusops.platform.mapper;

import com.nexusops.platform.domain.AuditLog;
import com.nexusops.platform.dto.AuditLogDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogMapper INSTANCE = Mappers.getMapper(AuditLogMapper.class);

    AuditLogDto toDto(AuditLog auditLog);
}