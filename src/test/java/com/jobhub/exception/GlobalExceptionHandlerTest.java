package com.jobhub.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void tooManyRequestsIncludesRemainingCooldownInBodyAndHeader() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ProblemDetail> response = handler.handleTooManyRequests(
                new TooManyRequestsException(
                        "Please wait before requesting another OTP", 43));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("43");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("retryAfterSeconds", 43L);
    }
}
