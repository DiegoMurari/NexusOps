package com.nexusops.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Um ativo vinculado a um ticket, com os dados mínimos para exibir na tela do ticket. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkedAssetDto {

    private String assetId;
    private String assetTag;
    private String name;
    private String type;
    private String lifecycleStatus;
    private String linkedBy;
    private Instant linkedAt;
}
