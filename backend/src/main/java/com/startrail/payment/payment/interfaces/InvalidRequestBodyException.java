package com.startrail.payment.payment.interfaces;

public class InvalidRequestBodyException extends RuntimeException {
    public InvalidRequestBodyException() {
        super("请求内容不是有效的 JSON");
    }
}
