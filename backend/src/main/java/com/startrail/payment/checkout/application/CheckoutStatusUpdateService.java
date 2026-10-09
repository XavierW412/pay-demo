package com.startrail.payment.checkout.application;

import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentDetails;
import com.startrail.payment.payment.application.PaymentStatusApplicationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutStatusUpdateService {
    private final PaymentStatusApplicationService statusApplicationService;

    public CheckoutStatusUpdateService(PaymentStatusApplicationService statusApplicationService) {
        this.statusApplicationService = statusApplicationService;
    }

    @Transactional
    public void apply(UUID paymentOrderId, AirwallexPaymentIntentDetails providerIntent) {
        statusApplicationService.applyVerifiedStatus(paymentOrderId, providerIntent);
    }
}
