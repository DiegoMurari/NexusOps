package com.nexusops.platform.dto;

import com.nexusops.platform.domain.SystemSetting;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettingDto {

    private String id;
    private String settingKey;
    private String value;
    private String valueType;
    private String description;
    private boolean isPublic;
    private String validationSchema;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}