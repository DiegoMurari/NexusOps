package com.nexusops.asset.mapper;

import com.nexusops.asset.domain.SoftwareLicense;
import com.nexusops.asset.dto.CreateSoftwareLicenseRequest;
import com.nexusops.asset.dto.SoftwareLicenseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SoftwareLicenseMapper {

    SoftwareLicenseDto toDto(SoftwareLicense license);

    SoftwareLicense toEntity(CreateSoftwareLicenseRequest request);
}
