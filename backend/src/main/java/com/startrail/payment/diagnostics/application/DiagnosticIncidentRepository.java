package com.startrail.payment.diagnostics.application;

import com.startrail.payment.diagnostics.domain.DiagnosticIncident;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosticIncidentRepository {
    void lockFingerprint(String fingerprint);
    Optional<DiagnosticIncident> findRecentOpenByFingerprint(String fingerprint, Instant cutoff);
    boolean recordOccurrence(UUID id, Instant receivedAt);
    void insert(DiagnosticIncident incident);
    Optional<DiagnosticIncident> find(UUID id, boolean lock);
    List<DiagnosticIncident> list(Query query);
    long count(Query query);
    List<DiagnosticIncident.History> history(UUID id);
    void handle(UUID id, String status, String actor, String action, String note, Instant now);

    record Query(UUID diagnosticId, Instant from, Instant to, String module, String severity,
            String status, int page, int size) {}
}
