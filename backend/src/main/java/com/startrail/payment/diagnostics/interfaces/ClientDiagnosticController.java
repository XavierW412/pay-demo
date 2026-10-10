package com.startrail.payment.diagnostics.interfaces;

import com.startrail.payment.diagnostics.application.DiagnosticIncidentService;
import com.startrail.payment.diagnostics.infrastructure.ClientDiagnosticRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diagnostics/client-events")
public class ClientDiagnosticController {
    private static final Set<String> FIELDS = Set.of("occurredAt", "environment", "appVersion", "browserFamily", "category",
            "severity", "module", "routeTemplate", "requestId", "traceId", "relatedDiagnosticId", "summary", "userDescription");
    private final DiagnosticIncidentService service;
    private final DiagnosticRequestReader reader;
    private final ClientDiagnosticRateLimiter limiter;
    public ClientDiagnosticController(DiagnosticIncidentService service, DiagnosticRequestReader reader,
            ClientDiagnosticRateLimiter limiter) {
        this.service = service;
        this.reader = reader;
        this.limiter = limiter;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DiagnosticIncidentResponse.Receipt receive(HttpServletRequest request) throws IOException {
        limiter.acquire(request);
        var receipt = service.receive(reader.read(request, FIELDS, DiagnosticIncidentRequest.class, 8192).toCommand());
        return new DiagnosticIncidentResponse.Receipt(receipt.diagnosticId(), receipt.receivedAt());
    }
}
