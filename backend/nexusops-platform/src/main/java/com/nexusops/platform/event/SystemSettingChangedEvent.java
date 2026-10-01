package com.nexusops.platform.event;

import com.nexusops.shared.event.DomainEvent;

public class SystemSettingChangedEvent extends DomainEvent {

    private final String settingKey;
    private final String value;
    private final String valueType;
    private final String changedBy;

    public SystemSettingChangedEvent(String aggregateId, long aggregateVersion, String settingKey, 
                                     String value, String valueType, String changedBy) {
        super(aggregateId, aggregateVersion);
        this.settingKey = settingKey;
        this.value = value;
        this.valueType = valueType;
        this.changedBy = changedBy;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getValue() {
        return value;
    }

    public String getValueType() {
        return valueType;
    }

    public String getChangedBy() {
        return changedBy;
    }
}