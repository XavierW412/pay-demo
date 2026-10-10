package com.startrail.payment.diagnostics.domain;

import java.time.Instant;
import java.util.UUID;

public record DiagnosticIncident(UUID diagnosticId, String fingerprint, Instant occurredAt, Instant receivedAt,
        Instant latestReceivedAt, long occurrenceCount,
        String environment, String appVersion, String browserFamily, String category, String severity, String module,
        String routeTemplate, String requestId, String traceId, String relatedDiagnosticId,
        UUID paymentOrderId, String summary, String userDescription, String status, Instant handledAt) {
    public record History(long id, String actor, String action, String note, Instant createdAt) {}
}
