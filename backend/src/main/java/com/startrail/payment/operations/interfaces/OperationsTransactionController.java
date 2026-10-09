package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsTransactionQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsTransactionController {
    private final OperationsTransactionQueryService queryService;

    public OperationsTransactionController(OperationsTransactionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/transactions")
    public OperationsTransactionQueryService.TransactionPage list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.list(query, status, page, size);
    }

    @GetMapping("/transactions/{paymentOrderId}")
    public OperationsTransactionQueryService.TransactionDetail detail(@PathVariable UUID paymentOrderId) {
        return queryService.detail(paymentOrderId);
    }

    @GetMapping("/audit")
    public List<OperationsTransactionQueryService.OperatorAudit> audit(@RequestParam(defaultValue = "100") int limit) {
        return queryService.latestAudit(limit);
    }
}
