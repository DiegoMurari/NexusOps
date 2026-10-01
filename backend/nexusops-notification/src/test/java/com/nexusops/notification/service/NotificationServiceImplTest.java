package com.nexusops.notification.service;

import com.nexusops.notification.domain.Notification;
import com.nexusops.notification.domain.Template;
import com.nexusops.notification.dto.NotificationDto;
import com.nexusops.notification.mapper.NotificationMapper;
import com.nexusops.notification.repository.NotificationRepository;
import com.nexusops.notification.repository.TemplateRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private NotificationMapper notificationMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, templateRepository, notificationMapper);
    }

    @Test
    void sendNotification_rendersTemplateVariables() {
        Template template = Template.builder()
            .templateKey("sla.breached")
            .channel(Template.Channel.IN_APP)
            .subjectTemplate("SLA Breached: {{ticketNumber}}")
            .contentTemplate("SLA has been breached for ticket {{ticketNumber}}.")
            .build();
        when(templateRepository.findByTemplateKeyAndActiveTrue("sla.breached")).thenReturn(Optional.of(template));

        service.sendNotification("tenant-1", "user-1", "sla.breached", Map.of("ticketNumber", "TKT-123"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getRecipientId()).isEqualTo("user-1");
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getSubject()).isEqualTo("SLA Breached: TKT-123");
        assertThat(saved.getContent()).isEqualTo("SLA has been breached for ticket TKT-123.");
        assertThat(saved.getStatus()).isEqualTo(Notification.Status.SENT);
        assertThat(saved.getChannel()).isEqualTo(Notification.Channel.IN_APP);
    }

    @Test
    void sendNotification_fallsBackToTemplateKeyWhenTemplateMissing() {
        when(templateRepository.findByTemplateKeyAndActiveTrue("unknown.key")).thenReturn(Optional.empty());

        service.sendNotification("tenant-1", "user-1", "unknown.key", Map.of());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("unknown.key");
        assertThat(captor.getValue().getContent()).isNull();
    }

    @Test
    void markRead_setsReadFlagAndTimestampOnce() {
        Notification notification = Notification.builder().id("n1").recipientId("user-1").build();
        when(notificationRepository.findByIdAndRecipientId("n1", "user-1")).thenReturn(Optional.of(notification));
        when(notificationMapper.toDto(notification)).thenReturn(NotificationDto.builder().id("n1").read(true).build());

        NotificationDto result = service.markRead("n1", "user-1");

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        assertThat(notification.getStatus()).isEqualTo(Notification.Status.READ);
        assertThat(result.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_isIdempotentAndDoesNotResaveAlreadyReadNotification() {
        Notification notification = Notification.builder().id("n1").recipientId("user-1").read(true).build();
        when(notificationRepository.findByIdAndRecipientId("n1", "user-1")).thenReturn(Optional.of(notification));
        when(notificationMapper.toDto(notification)).thenReturn(NotificationDto.builder().id("n1").read(true).build());

        service.markRead("n1", "user-1");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_throwsWhenNotificationDoesNotBelongToUser() {
        when(notificationRepository.findByIdAndRecipientId("n1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("n1", "user-2"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllRead_marksEveryUnreadNotificationForThatUserAndTenant() {
        Notification n1 = Notification.builder().id("n1").recipientId("user-1").tenantId("t1").build();
        Notification n2 = Notification.builder().id("n2").recipientId("user-1").tenantId("t1").build();
        when(notificationRepository.findByRecipientIdAndTenantIdAndReadFalse("user-1", "t1")).thenReturn(List.of(n1, n2));

        service.markAllRead("user-1", "t1");

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }
}
