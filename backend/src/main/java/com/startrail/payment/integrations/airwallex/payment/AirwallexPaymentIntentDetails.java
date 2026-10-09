package com.startrail.payment.integrations.airwallex.payment;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AirwallexPaymentIntentDetails(
        String id,
        BigDecimal amount,
        String currency,
        String status,
        @JsonProperty("merchant_order_id") String merchantOrderId,
        @JsonProperty("client_secret") String clientSecret) {
}
