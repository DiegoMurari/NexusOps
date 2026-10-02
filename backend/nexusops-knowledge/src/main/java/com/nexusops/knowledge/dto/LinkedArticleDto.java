package com.nexusops.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Um artigo vinculado a um ticket, com os dados mínimos para exibir na tela do ticket. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkedArticleDto {

    private String articleId;
    private String title;
    private String status;
    private String linkedBy;
    private Instant linkedAt;
}
