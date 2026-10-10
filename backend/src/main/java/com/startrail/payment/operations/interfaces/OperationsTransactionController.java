package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsTransactionQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String refundStatus,
            @RequestParam(required = false) String notificationStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.list(query, status, currency, provider, refundStatus,
                notificationStatus, from, to, page, size);
    }

    @GetMapping("/transactions/{paymentOrderId}")
    public OperationsTransactionQueryService.TransactionDetail detail(@PathVariable UUID paymentOrderId) {
        return queryService.detail(paymentOrderId);
    }

}
