package com.jobhub.exception;

import org.springframework.http.HttpStatus;

/** An error with an explicit HTTP status and machine-readable code, for statuses without their own exception (413, 415). */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
