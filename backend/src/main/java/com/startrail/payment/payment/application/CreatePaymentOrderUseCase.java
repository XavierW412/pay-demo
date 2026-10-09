package com.startrail.payment.payment.application;

public interface CreatePaymentOrderUseCase {
    CreatePaymentOrderResult execute(CreatePaymentOrderCommand command);
}
