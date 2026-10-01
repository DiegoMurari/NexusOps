package com.nexusops.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {

    @NotBlank
    @Size(max = 10000)
    private String content;

    private boolean publicComment = false;

    private String mentions;
}