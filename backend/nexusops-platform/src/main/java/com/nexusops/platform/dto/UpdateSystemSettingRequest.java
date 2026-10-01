package com.nexusops.platform.dto;

import com.nexusops.platform.domain.SystemSetting;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemSettingRequest {

    private String value;

    private SystemSetting.ValueType valueType;

    @Size(max = 1000)
    private String description;

    private Boolean isPublic;

    private String validationSchema;

    @Size(max = 50)
    private String category;
}