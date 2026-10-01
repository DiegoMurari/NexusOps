package com.nexusops.shared.exception;

import java.util.UUID;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND",
              "%s not found with identifier: %s".formatted(resourceType, identifier),
              resourceType, identifier);
    }

    public ResourceNotFoundException(String resourceType, Long id) {
        this(resourceType, id.toString());
    }

    public ResourceNotFoundException(String resourceType, UUID id) {
        this(resourceType, id.toString());
    }
}