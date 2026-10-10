package com.startrail.payment.operations.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsAuditQueryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ACTIONS = Set.of(
            "INVITE_OPERATOR", "REGISTER_OPERATOR", "REVOKE_OPERATOR_INVITATION", "DISABLE_OPERATOR",
            "RESTORE_OPERATOR", "REFRESH_PROVIDER_STATUS", "REFRESH_REFUND_STATUS", "RETRY_NOTIFICATION",
            "ADD_EXCEPTION_NOTE", "REFUND_CONFIRM_AND_SUBMIT", "ACKNOWLEDGE_DIAGNOSTIC",
            "ADD_DIAGNOSTIC_NOTE", "RESOLVE_DIAGNOSTIC");
    private static final Set<String> OUTCOMES = Set.of(
            "SUCCEEDED", "FAILED", "BLOCKED", "REJECTED", "CONFIRMED",
            "NOT_FOUND", "MISMATCH", "PROVIDER_UNAVAILABLE");
    private final JdbcTemplate jdbcTemplate;

    public OperationsAuditQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditPage list(String from, String to, String actor, String action, String outcome,
            String businessOrderId, UUID paymentOrderId, int page, int size) {
        validatePage(page, size);
        var start = parseDate(from, "开始日期");
        var end = parseDate(to, "结束日期");
        if ((start == null) != (end == null)) {
            throw new InvalidOperatorRequestException("请同时选择开始日期和结束日期");
        }
        if (start != null && (end.isBefore(start) || end.isAfter(start.plusDays(92)))) {
            throw new InvalidOperatorRequestException("审计查询日期范围无效，最多 93 天");
        }
        validateFilter(action, ACTIONS, "操作类型");
        validateFilter(outcome, OUTCOMES, "操作结果");

        var where = new StringBuilder(" WHERE 1=1");
        var parameters = new ArrayList<Object>();
        addLike(where, parameters, "a.username", actor, "操作人");
        addEquals(where, parameters, "l.action", action, "操作类型");
        addEquals(where, parameters, "l.outcome", outcome, "操作结果");
        addLike(where, parameters, "p.business_order_id", businessOrderId, "业务订单号");
        if (paymentOrderId != null) {
            where.append(" AND l.payment_order_id = ?");
            parameters.add(paymentOrderId);
        }
        if (start != null) {
            where.append(" AND l.created_at >= ?");
            parameters.add(java.sql.Timestamp.from(start.atStartOfDay(ZONE).toInstant()));
        }
        if (end != null) {
            where.append(" AND l.created_at < ?");
            parameters.add(java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant()));
        }

        var fromSql = " FROM operator_audit l JOIN operator_account a ON a.id = l.actor_id "
                + "LEFT JOIN payment_order p ON p.id = l.payment_order_id" + where;
        var total = jdbcTemplate.queryForObject("SELECT count(*)" + fromSql, Long.class, parameters.toArray());
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        var items = jdbcTemplate.query("""
                SELECT l.id, a.username AS actor, l.payment_order_id, p.business_order_id,
                       l.action, l.outcome, l.detail, l.created_at
                """ + fromSql + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AuditRow(rs.getLong("id"), rs.getString("actor"),
                        rs.getObject("payment_order_id", UUID.class), rs.getString("business_order_id"),
                        rs.getString("action"), rs.getString("outcome"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()), pageParameters.toArray());
        return new AuditPage(items, page, size, total == null ? 0 : total);
    }

    private LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new InvalidOperatorRequestException(label + "格式无效，应为 YYYY-MM-DD");
        }
    }

    private void addLike(StringBuilder where, List<Object> parameters, String column, String value, String label) {
        if (value == null || value.isBlank()) return;
        var normalized = value.trim();
        validateLength(normalized, 100, label);
        where.append(" AND strpos(lower(").append(column).append("), lower(?)) > 0");
        parameters.add(normalized);
    }

    private void addEquals(StringBuilder where, List<Object> parameters, String column, String value, String label) {
        if (value == null || value.isBlank()) return;
        var normalized = value.trim();
        validateLength(normalized, 64, label);
        where.append(" AND ").append(column).append(" = ?");
        parameters.add(normalized);
    }

    private void validateLength(String value, int max, String label) {
        if (value.length() > max) throw new InvalidOperatorRequestException(label + "筛选内容过长");
    }

    private void validateFilter(String value, Set<String> allowed, String label) {
        if (value != null && !value.isBlank() && !allowed.contains(value.trim())) {
            throw new InvalidOperatorRequestException(label + "筛选值无效");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidOperatorRequestException("分页参数无效");
    }

    public record AuditRow(long id, String actor, UUID paymentOrderId, String businessOrderId,
            String action, String outcome, String detail, Instant createdAt) {}

    public record AuditPage(List<AuditRow> items, int page, int size, long total) {}
}
