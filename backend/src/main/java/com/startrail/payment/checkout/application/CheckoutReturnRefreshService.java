package com.startrail.payment.checkout.application;

import com.startrail.payment.checkout.interfaces.CheckoutStatusResponse;
import com.startrail.payment.payment.application.PaymentStatusVerificationService;
import org.springframework.stereotype.Service;

@Service
public class CheckoutReturnRefreshService {
    private final CheckoutReturnTokenService returnTokenService;
    private final PaymentStatusVerificationService verificationService;
    private final CheckoutStatusQueryService statusQueryService;

    public CheckoutReturnRefreshService(
            CheckoutReturnTokenService returnTokenService,
            PaymentStatusVerificationService verificationService,
            CheckoutStatusQueryService statusQueryService) {
        this.returnTokenService = returnTokenService;
        this.verificationService = verificationService;
        this.statusQueryService = statusQueryService;
    }

    public CheckoutStatusResponse refresh(String opaqueToken) {
        var paymentOrderId = returnTokenService.findValidPaymentOrderId(opaqueToken)
                .orElseThrow(CheckoutContextNotFoundException::new);
        verificationService.verifyByPaymentOrderId(paymentOrderId);
        return statusQueryService.getStatusForPaymentOrder(paymentOrderId);
    }
}
