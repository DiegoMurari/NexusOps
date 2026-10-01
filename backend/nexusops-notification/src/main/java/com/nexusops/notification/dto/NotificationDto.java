package com.nexusops.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private String id;
    private String templateKey;
    private String subject;
    private String content;
    private String priority;
    private String status;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
