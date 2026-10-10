package com.startrail.payment.operations.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsFinanceOverviewQueryService {
    private static final Set<String> CURRENCIES = Set.of("CNY", "USD");
    private static final ZoneId OPERATIONS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    public OperationsFinanceOverviewQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FinanceOverview query(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(OPERATIONS_ZONE))
                || from.plusDays(92).isBefore(to)) {
            throw new InvalidOperatorRequestException("日期范围无效，最多查询 93 天且不能晚于今天");
        }
        var start = from.atStartOfDay(OPERATIONS_ZONE).toInstant();
        var end = to.plusDays(1).atStartOfDay(OPERATIONS_ZONE).toInstant();
        var payments = paymentAmounts(start, end);
        var refunds = refundAmounts(start, end);
        var paymentExceptions = paymentExceptions(start, end);
        var refundExceptions = refundExceptions(start, end);

        var summaries = CURRENCIES.stream().sorted().map(currency -> {
            var payment = payments.getOrDefault(currency, new AmountSummary(BigDecimal.ZERO, 0));
            var refund = refunds.getOrDefault(currency, RefundSummary.empty());
            return new CurrencySummary(currency, payment.amount().toPlainString(), payment.count(),
                    refund.awaitingConfirmation().toPlainString(), refund.processing().toPlainString(),
                    refund.succeeded().toPlainString(), refund.blockedCount(),
                    paymentExceptions.getOrDefault(currency, 0L), refundExceptions.getOrDefault(currency, 0L),
                    "NOT_CONNECTED", null, null);
        }).toList();
        return new FinanceOverview(from, to, "AIRWALLEX_SETTLEMENT_REPORT_NOT_CONNECTED", summaries);
    }

    private Map<String, AmountSummary> paymentAmounts(Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT currency,
                       COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCEEDED'), 0) AS succeeded_amount,
                       COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded_count
                FROM payment_order
                WHERE payment_provider = 'AIRWALLEX' AND created_at >= ? AND created_at < ?
                GROUP BY currency
                """, (rs, row) -> Map.entry(rs.getString("currency"),
                new AmountSummary(rs.getBigDecimal("succeeded_amount"), rs.getLong("succeeded_count"))),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, RefundSummary> refundAmounts(Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT r.currency,
                       COALESCE(SUM(r.amount) FILTER (WHERE r.status = 'PENDING_OPERATOR_CONFIRMATION'), 0) AS awaiting_amount,
                       COALESCE(SUM(r.amount) FILTER (WHERE r.status IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW')), 0) AS processing_amount,
                       COALESCE(SUM(r.amount) FILTER (WHERE r.status = 'SUCCEEDED'), 0) AS succeeded_amount,
                       COUNT(*) FILTER (WHERE r.status IN ('BLOCKED', 'NEEDS_REVIEW')) AS blocked_count
                FROM refund_order r
                JOIN payment_order p ON p.id = r.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX' AND r.created_at >= ? AND r.created_at < ?
                GROUP BY r.currency
                """, (rs, row) -> Map.entry(rs.getString("currency"),
                new RefundSummary(rs.getBigDecimal("awaiting_amount"), rs.getBigDecimal("processing_amount"),
                        rs.getBigDecimal("succeeded_amount"), rs.getLong("blocked_count"))),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Long> paymentExceptions(Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT p.currency, COUNT(*) AS exception_count
                FROM payment_order p
                JOIN LATERAL (
                    SELECT c.outcome FROM payment_reconciliation_check c
                    WHERE c.payment_order_id = p.id ORDER BY c.checked_at DESC, c.id DESC LIMIT 1
                ) latest ON latest.outcome IN ('DIFFERENCE', 'QUERY_FAILED')
                WHERE p.payment_provider = 'AIRWALLEX' AND p.created_at >= ? AND p.created_at < ?
                GROUP BY p.currency
                """, (rs, row) -> Map.entry(rs.getString("currency"), rs.getLong("exception_count")),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Long> refundExceptions(Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT r.currency, COUNT(*) AS exception_count
                FROM refund_order r
                JOIN payment_order p ON p.id = r.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX' AND r.status IN ('BLOCKED', 'NEEDS_REVIEW')
                  AND r.created_at >= ? AND r.created_at < ?
                GROUP BY r.currency
                """, (rs, row) -> Map.entry(rs.getString("currency"), rs.getLong("exception_count")),
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record AmountSummary(BigDecimal amount, long count) {
    }

    private record RefundSummary(BigDecimal awaitingConfirmation, BigDecimal processing,
            BigDecimal succeeded, long blockedCount) {
        private static RefundSummary empty() {
            return new RefundSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
    }

    public record CurrencySummary(String currency, String paymentSucceededAmount, long paymentSucceededCount,
            String refundAwaitingConfirmationAmount, String refundProcessingAmount,
            String refundSucceededAmount, long blockedRefundCount, long paymentExceptionCount,
            long refundExceptionCount, String settlementStatus, String settlementAmount, String settlementFee) {
    }

    public record FinanceOverview(LocalDate from, LocalDate to, String settlementSourceStatus,
            List<CurrencySummary> currencies) {
    }
}
