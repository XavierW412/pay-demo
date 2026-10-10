package com.startrail.payment.diagnostics.interfaces;

import com.startrail.payment.diagnostics.application.DiagnosticIncidentService;
import java.time.Instant;

public record DiagnosticIncidentRequest(Instant occurredAt, String environment, String appVersion, String browserFamily,
        String category, String severity, String module, String routeTemplate, String requestId,
        String traceId, String relatedDiagnosticId, String summary, String userDescription) {
    public DiagnosticIncidentService.ClientEvent toCommand() {
        return new DiagnosticIncidentService.ClientEvent(occurredAt, environment, appVersion, browserFamily, category,
                severity, module, routeTemplate, requestId, traceId, relatedDiagnosticId, summary, userDescription);
    }
}
