package com.nexusops.notification.service;

import com.nexusops.notification.domain.Notification;
import com.nexusops.notification.domain.Template;
import com.nexusops.notification.dto.NotificationDto;
import com.nexusops.notification.mapper.NotificationMapper;
import com.nexusops.notification.repository.NotificationRepository;
import com.nexusops.notification.repository.TemplateRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements com.nexusops.shared.notify.NotificationService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void sendNotification(String tenantId, String userId, String templateKey, Map<String, Object> variables) {
        Template template = templateRepository.findByTemplateKeyAndActiveTrue(templateKey).orElse(null);

        String subject = template != null ? render(template.getSubjectTemplate(), variables) : templateKey;
        String content = template != null ? render(template.getContentTemplate(), variables) : null;

        Notification notification = Notification.builder()
            .recipientId(userId)
            .tenantId(tenantId)
            .channel(Notification.Channel.IN_APP)
            .templateKey(templateKey)
            .subject(subject)
            .content(content)
            .status(Notification.Status.SENT)
            .sentAt(Instant.now())
            .build();

        notificationRepository.save(notification);
        log.debug("Notification sent: recipient={}, templateKey={}", userId, templateKey);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> listForUser(String userId, String tenantId, Pageable pageable) {
        return notificationRepository.findByRecipientIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId, pageable)
            .map(notificationMapper::toDto);
    }

    @Transactional
    public NotificationDto markRead(String id, String userId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification.setStatus(Notification.Status.READ);
            notificationRepository.save(notification);
        }
        return notificationMapper.toDto(notification);
    }

    @Transactional
    public void markAllRead(String userId, String tenantId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndTenantIdAndReadFalse(userId, tenantId);
        Instant now = Instant.now();
        for (Notification notification : unread) {
            notification.setRead(true);
            notification.setReadAt(now);
            notification.setStatus(Notification.Status.READ);
        }
        notificationRepository.saveAll(unread);
    }

    private String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = variables != null ? variables.get(matcher.group(1)) : null;
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
