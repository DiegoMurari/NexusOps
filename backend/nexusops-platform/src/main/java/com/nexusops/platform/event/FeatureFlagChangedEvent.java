package com.nexusops.platform.event;

import com.nexusops.shared.event.DomainEvent;

public class FeatureFlagChangedEvent extends DomainEvent {

    private final String key;
    private final String name;
    private final boolean enabled;
    private final int rolloutPercentage;
    private final String changedBy;

    public FeatureFlagChangedEvent(String aggregateId, long aggregateVersion, String key, String name, 
                                   boolean enabled, int rolloutPercentage, String changedBy) {
        super(aggregateId, aggregateVersion);
        this.key = key;
        this.name = name;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.changedBy = changedBy;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public String getChangedBy() {
        return changedBy;
    }
}