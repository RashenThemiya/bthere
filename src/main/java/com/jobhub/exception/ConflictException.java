package com.jobhub.exception;

/** 409. {@code code} is an optional machine-readable reason (for example CHAT_CLOSED) added to the error body. */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
