package com.nexusops.shared.exception;

import jakarta.validation.ConstraintViolation;
import java.util.Set;

public class ValidationException extends BusinessException {
    private final Set<ConstraintViolation<?>> violations;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.violations = Set.of();
    }

    public ValidationException(String message, Set<ConstraintViolation<?>> violations) {
        super("VALIDATION_ERROR", message);
        this.violations = violations;
    }

    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, cause);
        this.violations = Set.of();
    }

    public Set<ConstraintViolation<?>> getViolations() {
        return violations;
    }
}