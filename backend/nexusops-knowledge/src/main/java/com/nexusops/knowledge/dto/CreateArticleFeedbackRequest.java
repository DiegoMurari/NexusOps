package com.nexusops.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateArticleFeedbackRequest {

    @NotNull
    private Boolean helpful;

    @Size(max = 2000)
    private String comment;
}
