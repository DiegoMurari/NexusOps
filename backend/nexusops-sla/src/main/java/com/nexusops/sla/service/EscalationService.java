package com.nexusops.sla.service;

import com.nexusops.sla.domain.EscalationRule;
import com.nexusops.sla.domain.SlaBreach;
import com.nexusops.sla.repository.EscalationRuleRepository;
import com.nexusops.sla.repository.SlaBreachRepository;
import com.nexusops.shared.notify.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService {

    private final EscalationRuleRepository escalationRuleRepository;
    private final SlaBreachRepository slaBreachRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void processEscalations() {
        log.debug("Processing SLA escalations");
        
        List<SlaBreach> pendingEscalations = slaBreachRepository.findAllPendingEscalation(Instant.now());

        for (SlaBreach breach : pendingEscalations) {
            processBreachEscalation(breach);
        }
    }

    @Transactional
    public void processBreachEscalation(SlaBreach breach) {
        if (breach.isEscalated()) {
            return;
        }

        List<EscalationRule> rules = escalationRuleRepository.findBySlaDefinitionId(breach.getSlaDefinitionId());
        
        for (EscalationRule rule : rules) {
            if (!rule.isActive()) continue;
            
            if (shouldTrigger(rule, breach)) {
                executeActions(rule, breach);
            }
        }

        breach.setEscalated(true);
        breach.setEscalatedAt(Instant.now());
        slaBreachRepository.save(breach);
    }

    private boolean shouldTrigger(EscalationRule rule, SlaBreach breach) {
        return switch (rule.getTriggerType()) {
            case RESPONSE_TIME -> breach.getBreachType() == SlaBreach.BreachType.RESPONSE;
            case RESOLUTION_TIME -> breach.getBreachType() == SlaBreach.BreachType.RESOLUTION;
            case PERCENTAGE_THRESHOLD -> breach.getBreachPercentage() != null 
                && breach.getBreachPercentage() >= rule.getTriggerPercentage();
        };
    }

    private void executeActions(EscalationRule rule, SlaBreach breach) {
        for (EscalationRule.EscalationAction action : rule.getActions()) {
            try {
                executeAction(action, breach);
            } catch (Exception e) {
                log.error("Failed to execute escalation action: {}", action.getType(), e);
            }
        }
    }

    private void executeAction(EscalationRule.EscalationAction action, SlaBreach breach) {
        switch (action.getType()) {
            case NOTIFY_USER -> {
                if (action.getTargetUserId() != null && action.getTemplateKey() != null) {
                    notificationService.sendNotification(breach.getTenantId(), action.getTargetUserId(), action.getTemplateKey(),
                        Map.of("breachId", breach.getId(), "ticketId", breach.getTicketId()));
                }
            }
            case NOTIFY_GROUP -> {
                // Implementation would notify all users in group
            }
            case REASSIGN -> {
                // Implementation would reassign ticket
            }
            case WEBHOOK -> {
                if (action.getWebhookUrl() != null) {
                    // Implementation would call webhook
                }
            }
            case CREATE_TASK -> {
                // Implementation would create task
            }
        }
    }
}