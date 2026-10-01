package com.nexusops.platform.event;

import com.nexusops.shared.event.DomainEvent;

public class TenantCreatedEvent extends DomainEvent {

    private final String name;
    private final String domain;
    private final String subscriptionTier;

    public TenantCreatedEvent(String aggregateId, long aggregateVersion, String name, String domain, String subscriptionTier) {
        super(aggregateId, aggregateVersion);
        this.name = name;
        this.domain = domain;
        this.subscriptionTier = subscriptionTier;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getSubscriptionTier() {
        return subscriptionTier;
    }
}