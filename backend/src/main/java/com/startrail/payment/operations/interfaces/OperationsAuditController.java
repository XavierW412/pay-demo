package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsAuditQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/audit")
public class OperationsAuditController {
    private final OperationsAuditQueryService queryService;

    public OperationsAuditController(OperationsAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public OperationsAuditQueryService.AuditPage list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String businessOrderId,
            @RequestParam(required = false) UUID paymentOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer limit) {
        if (limit != null) {
            page = 0;
            size = limit;
        }
        return queryService.list(from, to, actor, action, outcome, businessOrderId, paymentOrderId, page, size);
    }
}
