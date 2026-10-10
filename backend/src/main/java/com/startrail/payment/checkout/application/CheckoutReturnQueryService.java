package com.startrail.payment.checkout.application;

import com.startrail.payment.checkout.interfaces.CheckoutStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class CheckoutReturnQueryService {
    private final CheckoutReturnTokenService returnTokenService;
    private final CheckoutStatusQueryService statusQueryService;

    public CheckoutReturnQueryService(
            CheckoutReturnTokenService returnTokenService,
            CheckoutStatusQueryService statusQueryService) {
        this.returnTokenService = returnTokenService;
        this.statusQueryService = statusQueryService;
    }

    public CheckoutStatusResponse getStatus(String opaqueToken) {
        var paymentOrderId = returnTokenService.findValidPaymentOrderId(opaqueToken)
                .orElseThrow(CheckoutContextNotFoundException::new);
        return statusQueryService.getStatusForPaymentOrder(paymentOrderId);
    }
}
