package com.jobhub.exception;

public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;
    private final String code;

    public TooManyRequestsException(String message) {
        this(message, 0);
    }

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        this(message, retryAfterSeconds, null);
    }

    public TooManyRequestsException(String message, long retryAfterSeconds, String code) {
        super(message);
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        this.code = code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }
}
