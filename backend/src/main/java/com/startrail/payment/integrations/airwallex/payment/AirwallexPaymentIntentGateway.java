package com.startrail.payment.integrations.airwallex.payment;

import com.startrail.payment.payment.domain.PaymentOrder;

public interface AirwallexPaymentIntentGateway {
    AirwallexPaymentIntent create(PaymentOrder paymentOrder, String returnUrl);

    AirwallexPaymentIntentDetails retrieve(String paymentIntentId);
}
