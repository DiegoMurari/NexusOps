package com.nexusops.ticketing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AssignmentService {

    public String suggestAssignee(String tenantId, String categoryId, String priority) {
        log.debug("Suggesting assignee for tenant: {}, category: {}, priority: {}", tenantId, categoryId, priority);
        return null;
    }

    public List<String> getAvailableAgents(String tenantId, String groupId) {
        log.debug("Getting available agents for tenant: {}, group: {}", tenantId, groupId);
        return List.of();
    }
}