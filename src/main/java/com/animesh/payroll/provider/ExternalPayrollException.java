package com.animesh.payroll.provider;

import org.springframework.http.HttpStatusCode;

import java.time.Duration;

public class ExternalPayrollException extends RuntimeException {

    private final boolean retryable;
    private final HttpStatusCode statusCode;
    private final Duration retryAfter;

    public ExternalPayrollException(String message, Throwable cause) {
        this(message, cause, false, null, null);
    }

    public ExternalPayrollException(String message, Throwable cause,
                                    boolean retryable,
                                    HttpStatusCode statusCode,
                                    Duration retryAfter) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public boolean retryable() {
        return retryable;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
