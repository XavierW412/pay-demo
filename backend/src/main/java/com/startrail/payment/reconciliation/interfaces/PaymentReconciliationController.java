package com.startrail.payment.reconciliation.interfaces;

import com.startrail.payment.reconciliation.application.PaymentReconciliationService;
import com.startrail.payment.reconciliation.application.ReconciliationQueryService;
import com.startrail.payment.reconciliation.application.ReconciliationSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class PaymentReconciliationController {
    private final PaymentReconciliationService checkService;
    private final ReconciliationQueryService queryService;

    public PaymentReconciliationController(
            PaymentReconciliationService checkService,
            ReconciliationQueryService queryService) {
        this.checkService = checkService;
        this.queryService = queryService;
    }

    @PostMapping("/transactions/{paymentOrderId}/reconciliation-checks")
    public CheckResponse check(Authentication authentication, @PathVariable UUID paymentOrderId) {
        return response(checkService.checkByOperator(authentication.getName(), paymentOrderId));
    }

    @GetMapping("/transactions/{paymentOrderId}/reconciliation-checks")
    public List<CheckResponse> history(
            @PathVariable UUID paymentOrderId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return queryService.history(paymentOrderId, limit, offset).stream().map(this::response).toList();
    }

    @GetMapping("/reconciliation/differences")
    public List<CheckResponse> differences(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return queryService.latestDifferences(limit, offset).stream().map(this::response).toList();
    }

    private CheckResponse response(ReconciliationSnapshot snapshot) {
        return new CheckResponse("PAYMENT_STATUS_CHECK", false, snapshot);
    }

    public record CheckResponse(String checkType, boolean settlementReconciled, ReconciliationSnapshot snapshot) {
    }
}
