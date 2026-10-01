package com.nexusops.platform.dto;

import com.nexusops.platform.domain.SystemSetting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSystemSettingRequest {

    @NotBlank
    @Size(max = 100)
    private String settingKey;

    private String value;

    @NotNull
    private SystemSetting.ValueType valueType = SystemSetting.ValueType.STRING;

    @Size(max = 1000)
    private String description;

    private boolean isPublic = false;

    private String validationSchema;

    @Size(max = 50)
    private String category;
}