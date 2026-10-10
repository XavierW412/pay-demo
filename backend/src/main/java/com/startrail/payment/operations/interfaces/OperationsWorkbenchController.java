package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsWorkbenchQueryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/workbench")
public class OperationsWorkbenchController {
    private final OperationsWorkbenchQueryService queryService;

    public OperationsWorkbenchController(OperationsWorkbenchQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public OperationsWorkbenchQueryService.Workbench query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryService.query(from, to);
    }
}
