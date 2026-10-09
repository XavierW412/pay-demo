package com.startrail.payment.checkout.application;

public class CheckoutContextNotFoundException extends RuntimeException {
    public CheckoutContextNotFoundException() {
        super("付款页面已失效或不存在");
    }
}
