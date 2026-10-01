package com.nexusops.shared.exception;

public class BusinessException extends RuntimeException {
    private final String code;
    private final Object[] args;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.args = new Object[0];
    }

    public BusinessException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.args = new Object[0];
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }
}