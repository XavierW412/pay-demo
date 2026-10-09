package com.startrail.payment.payment.infrastructure;

import java.util.UUID;

public record PaymentStatusRefreshLease(UUID paymentOrderId, UUID token) {
}
