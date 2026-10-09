package com.startrail.payment.refund.application;

import org.springframework.http.HttpStatus;

public class RefundRequestException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String code;

    public RefundRequestException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
