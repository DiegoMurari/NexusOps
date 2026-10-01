package com.nexusops.iam.event;

import com.nexusops.shared.event.DomainEvent;

public class UserCreatedEvent extends DomainEvent {

    private final String email;
    private final String firstName;
    private final String lastName;
    private final String tenantId;

    public UserCreatedEvent(String aggregateId, long aggregateVersion, String email, String firstName, String lastName, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getTenantId() {
        return tenantId;
    }
}