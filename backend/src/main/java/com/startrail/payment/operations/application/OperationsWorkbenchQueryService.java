package com.startrail.payment.operations.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsWorkbenchQueryService {
    private static final ZoneId OPERATIONS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int ACTIVITY_LIMIT = 8;

    private final JdbcTemplate jdbcTemplate;
    private final OperationsExceptionQueryService exceptionQueryService;

    public OperationsWorkbenchQueryService(
            JdbcTemplate jdbcTemplate,
            OperationsExceptionQueryService exceptionQueryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.exceptionQueryService = exceptionQueryService;
    }

    public Workbench query(LocalDate from, LocalDate to) {
        var today = LocalDate.now(OPERATIONS_ZONE);
        var effectiveFrom = from == null && to == null ? today.minusDays(29) : from;
        var effectiveTo = from == null && to == null ? today : to;
        if (effectiveFrom == null || effectiveTo == null || effectiveFrom.isAfter(effectiveTo)
                || effectiveTo.isAfter(today) || effectiveFrom.plusDays(92).isBefore(effectiveTo)) {
            throw new InvalidOperatorRequestException("日期范围无效，最多查询 93 天且不能晚于今天");
        }

        var queues = List.of(
                queue("REFUND_CONFIRMATION", "待确认退款"),
                queue("REFUND_REVIEW", "退款异常"),
                queue("PAYMENT_DIFFERENCE", "支付差异"),
                queue("NOTIFICATION_FAILURE", "通知失败"));
        var recentActivity = recentActivity(effectiveFrom, effectiveTo);
        return new Workbench(Instant.now(), effectiveFrom, effectiveTo, queues, recentActivity);
    }

    private List<RecentActivity> recentActivity(LocalDate from, LocalDate to) {
        var start = from.atStartOfDay(OPERATIONS_ZONE).toInstant();
        var end = to.plusDays(1).atStartOfDay(OPERATIONS_ZONE).toInstant();
        var activities = new ArrayList<RecentActivity>();
        activities.addAll(jdbcTemplate.query("""
                SELECT n.event_id, p.id AS payment_order_id, p.business_order_id, p.amount, p.currency,
                       n.delivery_status, n.created_at
                FROM notification_outbox n JOIN payment_order p ON p.id = n.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX' AND n.event_type LIKE 'payment_intent.%'
                  AND n.created_at >= ? AND n.created_at < ?
                ORDER BY n.created_at DESC, n.event_id DESC LIMIT ?
                """, (rs, rowNum) -> new RecentActivity(
                rs.getString("event_id"), "PAYMENT_NOTIFICATION", "支付结果通知",
                rs.getObject("payment_order_id", java.util.UUID.class), rs.getString("business_order_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("delivery_status"), null,
                rs.getTimestamp("created_at").toInstant()),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), ACTIVITY_LIMIT));
        activities.addAll(jdbcTemplate.query("""
                SELECT r.id, r.payment_order_id, r.business_order_id, r.amount, r.currency,
                       r.status, r.updated_at
                FROM refund_order r JOIN payment_order p ON p.id = r.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX' AND r.updated_at >= ? AND r.updated_at < ?
                ORDER BY r.updated_at DESC, r.id DESC LIMIT ?
                """, (rs, rowNum) -> new RecentActivity(
                rs.getObject("id", java.util.UUID.class).toString(), "REFUND", "退款状态更新",
                rs.getObject("payment_order_id", java.util.UUID.class), rs.getString("business_order_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("status"), null, rs.getTimestamp("updated_at").toInstant()),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), ACTIVITY_LIMIT));
        activities.addAll(jdbcTemplate.query("""
                SELECT l.id, l.payment_order_id, p.business_order_id, l.action, l.outcome,
                       a.username, l.created_at
                FROM operator_audit l
                JOIN operator_account a ON a.id = l.actor_id
                LEFT JOIN payment_order p ON p.id = l.payment_order_id
                WHERE l.created_at >= ? AND l.created_at < ?
                  AND (l.payment_order_id IS NULL OR p.payment_provider = 'AIRWALLEX')
                ORDER BY l.created_at DESC, l.id DESC LIMIT ?
                """, (rs, rowNum) -> new RecentActivity(
                Long.toString(rs.getLong("id")), "OPERATOR_ACTION", rs.getString("action"),
                rs.getObject("payment_order_id", java.util.UUID.class), rs.getString("business_order_id"),
                null, null, rs.getString("outcome"), rs.getString("username"),
                rs.getTimestamp("created_at").toInstant()),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), ACTIVITY_LIMIT));
        return activities.stream()
                .sorted(Comparator.comparing(RecentActivity::occurredAt).reversed()
                        .thenComparing(RecentActivity::id, Comparator.reverseOrder()))
                .limit(15)
                .toList();
    }

    private WorkbenchQueue queue(String type, String label) {
        var page = exceptionQueryService.list(type, 0, 5);
        return new WorkbenchQueue(type, label, page.total(), page.items());
    }

    public record RecentActivity(
            String id, String type, String title, java.util.UUID paymentOrderId, String businessOrderId,
            String amount, String currency, String status, String actor, Instant occurredAt) {
    }

    public record WorkbenchQueue(String type, String label, long count,
            List<OperationsExceptionQueryService.ExceptionItem> items) {
    }

    public record Workbench(
            Instant generatedAt, LocalDate from, LocalDate to, List<WorkbenchQueue> queues,
            List<RecentActivity> recentActivity) {
    }
}
