package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.interfaces.CreatePaymentOrderResponse;
import java.math.BigDecimal;

public interface MockPaymentOrderClient {
    CreatePaymentOrderResponse create(
            String requestId,
            String businessOrderId,
            BigDecimal amount,
            String currency,
            String shopperCountryCode);
}
