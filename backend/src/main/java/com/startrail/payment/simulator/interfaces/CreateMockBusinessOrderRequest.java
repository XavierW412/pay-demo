package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.domain.MockPaymentMode;

public record CreateMockBusinessOrderRequest(String productCode, MockPaymentMode paymentMode) {
}
