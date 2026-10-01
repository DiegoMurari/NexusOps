package com.nexusops.iam.dto;

import com.nexusops.iam.domain.Permission;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionDto {

    private String id;
    private String permissionKey;
    private String resource;
    private String action;
    private String scope;
    private String description;
    private String category;
}