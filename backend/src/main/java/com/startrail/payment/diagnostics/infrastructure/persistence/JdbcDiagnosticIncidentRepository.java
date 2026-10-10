package com.startrail.payment.diagnostics.infrastructure.persistence;

import com.startrail.payment.diagnostics.application.DiagnosticIncidentRepository;
import com.startrail.payment.diagnostics.domain.DiagnosticIncident;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDiagnosticIncidentRepository implements DiagnosticIncidentRepository {
    private final JdbcTemplate jdbc;
    public JdbcDiagnosticIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockFingerprint(String fingerprint) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, fingerprint);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public Optional<DiagnosticIncident> findRecentOpenByFingerprint(String fingerprint, Instant cutoff) {
        return jdbc.query("""
                SELECT * FROM diagnostic_incident
                WHERE fingerprint = ? AND last_received_at >= ? AND status <> 'RESOLVED'
                    AND category <> 'USER_REPORT' AND user_description IS NULL
                ORDER BY last_received_at DESC, diagnostic_id DESC LIMIT 1
                FOR UPDATE
                """, this::map, fingerprint, Timestamp.from(cutoff)).stream().findFirst();
    }

    @Override
    public boolean recordOccurrence(UUID id, Instant receivedAt) {
        return jdbc.update("""
                UPDATE diagnostic_incident
                SET occurrence_count = occurrence_count + 1,
                    last_received_at = GREATEST(last_received_at, ?)
                WHERE diagnostic_id = ? AND status <> 'RESOLVED'
                """, Timestamp.from(receivedAt), id) == 1;
    }

    @Override
    public void insert(DiagnosticIncident event) {
        jdbc.update("""
                INSERT INTO diagnostic_incident (diagnostic_id, fingerprint, occurred_at, received_at,
                    last_received_at, occurrence_count,
                    environment, app_version, browser_family, category, severity, module, route_template, request_id,
                    trace_id, related_diagnostic_id, payment_order_id, summary, user_description, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.diagnosticId(), event.fingerprint(), Timestamp.from(event.occurredAt()),
                Timestamp.from(event.receivedAt()), Timestamp.from(event.latestReceivedAt()), event.occurrenceCount(),
                event.environment(), event.appVersion(), event.browserFamily(), event.category(),
                event.severity(), event.module(), event.routeTemplate(), event.requestId(), event.traceId(),
                event.relatedDiagnosticId(), event.paymentOrderId(), event.summary(), event.userDescription(), event.status());
    }

    @Override
    public Optional<DiagnosticIncident> find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM diagnostic_incident WHERE diagnostic_id = ?" + (lock ? " FOR UPDATE" : ""),
                this::map, id).stream().findFirst();
    }

    @Override
    public List<DiagnosticIncident> list(Query query) {
        Filter filter = filter(query);
        var parameters = new ArrayList<>(filter.parameters());
        parameters.add(query.size());
        parameters.add((long) query.page() * query.size());
        return jdbc.query("SELECT * FROM diagnostic_incident" + filter.sql()
                + " ORDER BY last_received_at DESC, diagnostic_id DESC LIMIT ? OFFSET ?", this::map, parameters.toArray());
    }

    @Override
    public long count(Query query) {
        Filter filter = filter(query);
        Long count = jdbc.queryForObject("SELECT count(*) FROM diagnostic_incident" + filter.sql(),
                Long.class, filter.parameters().toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<DiagnosticIncident.History> history(UUID id) {
        return jdbc.query("""
                SELECT id, actor, action, note, created_at FROM diagnostic_incident_history
                WHERE diagnostic_id = ? ORDER BY created_at ASC, id ASC
                """, (rs, row) -> new DiagnosticIncident.History(rs.getLong("id"), rs.getString("actor"),
                rs.getString("action"), rs.getString("note"), rs.getTimestamp("created_at").toInstant()), id);
    }

    @Override
    public void handle(UUID id, String status, String actor, String action, String note, Instant now) {
        jdbc.update("UPDATE diagnostic_incident SET status = ?, handled_at = ? WHERE diagnostic_id = ?",
                status, Timestamp.from(now), id);
        jdbc.update("""
                INSERT INTO diagnostic_incident_history (diagnostic_id, actor, action, note, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, actor, action, note, Timestamp.from(now));
    }

    private Filter filter(Query query) {
        var where = new StringBuilder(" WHERE 1=1");
        var parameters = new ArrayList<Object>();
        add(where, parameters, "diagnostic_id", query.diagnosticId());
        add(where, parameters, "module", query.module());
        add(where, parameters, "severity", query.severity());
        add(where, parameters, "status", query.status());
        if (query.from() != null) { where.append(" AND last_received_at >= ?"); parameters.add(Timestamp.from(query.from())); }
        if (query.to() != null) { where.append(" AND last_received_at < ?"); parameters.add(Timestamp.from(query.to())); }
        return new Filter(where.toString(), parameters);
    }
    private void add(StringBuilder where, List<Object> parameters, String column, Object value) {
        if (value != null) { where.append(" AND ").append(column).append(" = ?"); parameters.add(value); }
    }
    private DiagnosticIncident map(ResultSet rs, int row) throws SQLException {
        Timestamp handled = rs.getTimestamp("handled_at");
        return new DiagnosticIncident(rs.getObject("diagnostic_id", UUID.class), rs.getString("fingerprint"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("received_at").toInstant(),
                rs.getTimestamp("last_received_at").toInstant(), rs.getLong("occurrence_count"),
                rs.getString("environment"), rs.getString("app_version"), rs.getString("browser_family"), rs.getString("category"),
                rs.getString("severity"), rs.getString("module"), rs.getString("route_template"),
                rs.getString("request_id"), rs.getString("trace_id"), rs.getString("related_diagnostic_id"),
                rs.getObject("payment_order_id", UUID.class), rs.getString("summary"), rs.getString("user_description"),
                rs.getString("status"), handled == null ? null : handled.toInstant());
    }
    private record Filter(String sql, List<Object> parameters) {}
}
