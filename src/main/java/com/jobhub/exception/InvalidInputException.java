package com.jobhub.exception;

/** 400 with a machine-readable {@code code} in the error body. */
public class InvalidInputException extends IllegalArgumentException {
    private final String code;

    public InvalidInputException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
