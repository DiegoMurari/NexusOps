package com.nexusops.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetHistoryDto {

    private String id;
    private String eventType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String actor;
    private Instant createdAt;
}
