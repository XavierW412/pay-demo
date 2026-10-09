package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsFinanceOverviewQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/finance")
public class OperationsFinanceOverviewController {
    private final OperationsFinanceOverviewQueryService queryService;

    public OperationsFinanceOverviewController(OperationsFinanceOverviewQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    public OperationsFinanceOverviewQueryService.FinanceOverview overview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryService.query(from, to);
    }
}
