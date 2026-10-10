package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsRefundQueryService;
import com.startrail.payment.operations.application.OperationsRefundStatusQueryService;
import com.startrail.payment.refund.application.RefundExecutionService;
import com.startrail.payment.refund.interfaces.RefundResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsRefundController {
    private final OperationsRefundQueryService queryService;
    private final OperationsRefundStatusQueryService statusQueryService;
    private final RefundExecutionService executionService;

    public OperationsRefundController(OperationsRefundQueryService queryService,
            OperationsRefundStatusQueryService statusQueryService,
            RefundExecutionService executionService) {
        this.queryService = queryService;
        this.statusQueryService = statusQueryService;
        this.executionService = executionService;
    }

    @GetMapping("/transactions/{paymentOrderId}/refunds")
    public List<OperationsRefundQueryService.RefundRow> forPayment(@PathVariable UUID paymentOrderId) {
        return queryService.forPayment(paymentOrderId);
    }

    @PostMapping("/refunds/{refundId}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public RefundResponse confirm(Authentication authentication, @PathVariable UUID refundId) {
        var result = executionService.confirmAndSubmit(refundId, authentication.getName());
        return RefundResponse.from(result.refund());
    }

    @PostMapping("/refunds/{refundId}/refresh")
    public OperationsRefundStatusQueryService.RefundStatusResult refresh(
            Authentication authentication, @PathVariable UUID refundId) {
        return statusQueryService.refresh(authentication.getName(), refundId);
    }
}
