package com.startrail.payment.diagnostics.interfaces;

import com.startrail.payment.diagnostics.application.DiagnosticIncidentService;
import com.startrail.payment.diagnostics.domain.DiagnosticIncident;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DiagnosticIncidentResponse {
    private DiagnosticIncidentResponse() {}
    public record Receipt(UUID diagnosticId, Instant receivedAt) {}
    public record Detail(Incident incident, List<History> history) {
        public static Detail from(DiagnosticIncidentService.Detail detail, boolean includeFreeText) {
            return new Detail(Incident.from(detail.incident(), includeFreeText), detail.history().stream()
                    .map(entry -> new History(entry.id(), entry.actor(), entry.action(),
                            includeFreeText ? entry.note() : null, entry.createdAt())).toList());
        }
    }
    public record Incident(UUID diagnosticId, Instant occurredAt, Instant receivedAt,
            Instant latestReceivedAt, long occurrenceCount,
            String environment, String appVersion, String browserFamily, String category, String severity, String module,
            String routeTemplate, String requestId, String traceId, String relatedDiagnosticId,
            UUID paymentOrderId, String summary, String userDescription, String status, Instant handledAt) {
        public static Incident from(DiagnosticIncident event, boolean includeFreeText) {
            return new Incident(event.diagnosticId(), event.occurredAt(), event.receivedAt(),
                    event.latestReceivedAt(), event.occurrenceCount(),
                    event.environment(), event.appVersion(), event.browserFamily(), event.category(), event.severity(), event.module(),
                    event.routeTemplate(), event.requestId(), event.traceId(), event.relatedDiagnosticId(),
                    event.paymentOrderId(), event.summary(), includeFreeText ? event.userDescription() : null,
                    event.status(), event.handledAt());
        }
    }
    public record History(long id, String actor, String action, String note, Instant createdAt) {}
    public record Row(UUID diagnosticId, Instant occurredAt, Instant receivedAt,
            Instant latestReceivedAt, long occurrenceCount,
            String environment, String appVersion, String browserFamily, String category, String severity, String module,
            String routeTemplate, String summary, String status, Instant handledAt) {
        public static Row from(DiagnosticIncident event) {
            return new Row(event.diagnosticId(), event.occurredAt(), event.receivedAt(),
                    event.latestReceivedAt(), event.occurrenceCount(),
                    event.environment(), event.appVersion(), event.browserFamily(), event.category(), event.severity(), event.module(),
                    event.routeTemplate(), event.summary(), event.status(), event.handledAt());
        }
    }
    public record Page(List<Row> items, int page, int size, long total) {
        public static Page from(DiagnosticIncidentService.Page page) {
            return new Page(page.items().stream().map(Row::from).toList(), page.page(), page.size(), page.total());
        }
    }
}
