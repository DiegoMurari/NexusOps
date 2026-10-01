package com.nexusops.notification.mapper;

import com.nexusops.notification.domain.Notification;
import com.nexusops.notification.dto.NotificationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationDto toDto(Notification notification);
}
