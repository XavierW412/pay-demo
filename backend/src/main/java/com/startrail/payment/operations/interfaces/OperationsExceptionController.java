package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsExceptionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/exceptions")
public class OperationsExceptionController {
    private final OperationsExceptionQueryService queryService;

    public OperationsExceptionController(OperationsExceptionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public OperationsExceptionQueryService.ExceptionPage list(
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.list(type, page, size);
    }
}
