package com.startrail.payment.checkout.application;

import com.startrail.payment.checkout.interfaces.CheckoutStatusResponse;
import com.startrail.payment.payment.application.PaymentStatusVerificationService;
import org.springframework.stereotype.Service;

@Service
public class CheckoutStatusRefreshService {
    private final CheckoutTokenService checkoutTokenService;
    private final PaymentStatusVerificationService verificationService;
    private final CheckoutStatusQueryService statusQueryService;

    public CheckoutStatusRefreshService(
            CheckoutTokenService checkoutTokenService,
            PaymentStatusVerificationService verificationService,
            CheckoutStatusQueryService statusQueryService) {
        this.checkoutTokenService = checkoutTokenService;
        this.verificationService = verificationService;
        this.statusQueryService = statusQueryService;
    }

    public CheckoutStatusResponse refresh(String opaqueToken) {
        var token = checkoutTokenService.findValid(opaqueToken)
                .orElseThrow(CheckoutContextNotFoundException::new);
        verificationService.verifyByPaymentOrderId(token.paymentOrderId());
        return statusQueryService.getStatusForPaymentOrder(token.paymentOrderId());
    }
}
