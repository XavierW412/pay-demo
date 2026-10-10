package com.startrail.payment.simulator.application;

import com.startrail.payment.integrations.startrail.IntegrationSigner;
import com.startrail.payment.refund.interfaces.CreateRefundRequest;
import com.startrail.payment.refund.interfaces.RefundResponse;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import com.startrail.payment.simulator.domain.MockPaymentMode;
import com.startrail.payment.simulator.interfaces.MockRefundRequestResponse.RefundRequestRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local")
public class MockRefundInstructionService {
    private final MockBusinessOrderRepository orders;
    private final RestClient paymentService;
    private final IntegrationSigner signer;
    private final ObjectMapper objectMapper;
    private final String refundSecret;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    public MockRefundInstructionService(
            MockBusinessOrderRepository orders,
            RestClient.Builder builder,
            IntegrationSigner signer,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            @Value("${startrail.integration.refund-inbound-secret}") String refundSecret,
            @Value("${startrail.simulator.payment-service-url:http://127.0.0.1:8080}") String paymentServiceUrl) {
        this.orders = orders;
        this.paymentService = builder.baseUrl(paymentServiceUrl).build();
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = transactions;
        this.refundSecret = refundSecret;
    }

    public RefundRequestRow requestRefund(String businessOrderId, String requestId,
            BigDecimal amount, String reason) {
        var order = requireRefundableOrder(businessOrderId);
        if (requestId == null || !requestId.matches("[a-f0-9-]{36}")
                || amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() > 19 || amount.compareTo(order.amount()) > 0
                || reason != null && reason.length() > 128) {
            throw new InvalidMockPaymentEventException("模拟退款申请字段或金额无效");
        }
        var existing = findRequest(requestId);
        if (existing != null) {
            requireSameRequest(existing, businessOrderId, amount, order.currency(), reason);
            return existing;
        }
        return transactions.execute(transaction -> {
            lockRefundOrder(businessOrderId);
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                    rs -> { rs.next(); return null; }, "mock-refund-request:" + requestId);
            var lockedOrder = requireRefundableOrder(businessOrderId);
            var repeated = findRequest(requestId);
            if (repeated != null) {
                requireSameRequest(repeated, businessOrderId, amount, lockedOrder.currency(), reason);
                return repeated;
            }
            var reservedAmount = reservedAmount(businessOrderId);
            if (reservedAmount.add(amount).compareTo(lockedOrder.amount()) > 0) {
                var remaining = lockedOrder.amount().subtract(reservedAmount).max(BigDecimal.ZERO);
                throw new InvalidMockPaymentEventException("累计退款金额不能超过订单金额，当前可申请退款金额为 "
                        + remaining.setScale(2).toPlainString() + " " + lockedOrder.currency());
            }
            jdbcTemplate.update("""
                    INSERT INTO mock_xingzhe_refund_request(
                        refund_request_id, business_order_id, amount, currency, reason,
                        business_decision, payment_service_status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'REQUESTED', 'NOT_SENT', ?)
                    """, requestId, businessOrderId, amount, lockedOrder.currency(), reason,
                    java.sql.Timestamp.from(Instant.now()));
            return findRequest(requestId);
        });
    }

    private void lockRefundOrder(String businessOrderId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                rs -> { rs.next(); return null; }, "mock-refund-order:" + businessOrderId);
    }

    private BigDecimal reservedAmount(String businessOrderId) {
        var amount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM mock_xingzhe_refund_request
                WHERE business_order_id = ? AND business_decision <> 'REJECTED'
                    AND payment_service_status NOT IN ('FAILED', 'BLOCKED')
                """, BigDecimal.class, businessOrderId);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public RefundResponse approveAndSend(String businessOrderId, String refundRequestId) {
        var approved = transactions.execute(transaction -> {
            var order = requireRefundableOrder(businessOrderId);
            var request = lockRequest(refundRequestId);
            requireSameRequest(request, businessOrderId, new BigDecimal(request.amount()), order.currency(), request.reason());
            if ("REJECTED".equals(request.businessDecision())) {
                throw new InvalidMockPaymentEventException("已拒绝的模拟退款申请不能再批准");
            }
            if ("REQUESTED".equals(request.businessDecision())) {
                jdbcTemplate.update("""
                        UPDATE mock_xingzhe_refund_request
                        SET business_decision = 'APPROVED', decided_at = ?, last_sent_at = ?
                        WHERE refund_request_id = ? AND business_decision = 'REQUESTED'
                        """, java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()), refundRequestId);
            } else {
                jdbcTemplate.update("UPDATE mock_xingzhe_refund_request SET last_sent_at = ? WHERE refund_request_id = ?",
                        java.sql.Timestamp.from(Instant.now()), refundRequestId);
            }
            return new ApprovedRequest(order, findRequest(refundRequestId));
        });
        if (approved == null) {
            throw new IllegalStateException("无法批准模拟退款申请");
        }
        var order = approved.order();
        var request = approved.request();
        var command = new CreateRefundRequest(request.refundRequestId(), UUID.fromString(order.paymentOrderId()),
                businessOrderId, new BigDecimal(request.amount()), request.currency(), request.reason());
        final String body;
        try {
            body = objectMapper.writeValueAsString(command);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成模拟星辙退款指令", exception);
        }
        var timestamp = Long.toString(Instant.now().getEpochSecond());
        var nonce = UUID.randomUUID().toString();
        var signature = signer.sign(timestamp, nonce, body, refundSecret);
        var response = paymentService.post().uri("/api/v1/integration/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Startrail-Timestamp", timestamp)
                .header("X-Startrail-Nonce", nonce)
                .header("X-Startrail-Signature", signature)
                .body(body).retrieve().body(RefundResponse.class);
        if (response != null) {
            jdbcTemplate.update("""
                    UPDATE mock_xingzhe_refund_request
                    SET payment_service_status = ?, payment_refund_id = ?
                    WHERE refund_request_id = ?
                    """, response.status(), response.refundId(), refundRequestId);
        }
        return response;
    }

    public void reject(String businessOrderId, String refundRequestId) {
        requireRefundableOrder(businessOrderId);
        var updated = jdbcTemplate.update("""
                UPDATE mock_xingzhe_refund_request
                SET business_decision = 'REJECTED', decided_at = ?
                WHERE refund_request_id = ? AND business_order_id = ? AND business_decision = 'REQUESTED'
                """, java.sql.Timestamp.from(Instant.now()), refundRequestId, businessOrderId);
        if (updated == 0) {
            throw new InvalidMockPaymentEventException("退款申请不存在或已经处理");
        }
    }

    public List<RefundRequestRow> refundRequests(String businessOrderId) {
        orders.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return jdbcTemplate.query("""
                SELECT refund_request_id, business_order_id, amount, currency, reason,
                       business_decision, payment_service_status, business_outcome, provider_status,
                       raw_provider_status,
                       payment_refund_id,
                       created_at, decided_at, last_sent_at
                FROM mock_xingzhe_refund_request
                WHERE business_order_id = ?
                ORDER BY created_at DESC, refund_request_id DESC
                """, (rs, rowNum) -> new RefundRequestRow(
                rs.getString("refund_request_id"), rs.getString("business_order_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("reason"), rs.getString("business_decision"),
                rs.getString("payment_service_status"), rs.getString("business_outcome"),
                rs.getString("provider_status"), rs.getString("raw_provider_status"),
                rs.getString("payment_refund_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getTimestamp("last_sent_at") == null ? null : rs.getTimestamp("last_sent_at").toInstant()),
                businessOrderId);
    }

    public List<RefundEventRow> receivedEvents(String businessOrderId) {
        orders.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return jdbcTemplate.query("""
                SELECT refund_request_id, provider_refund_id, status, business_outcome,
                       execution_status, provider_status, raw_provider_status, amount, currency, received_at
                FROM mock_xingzhe_refund_event WHERE business_order_id = ?
                ORDER BY received_at DESC, event_id DESC LIMIT 100
                """, (rs, rowNum) -> new RefundEventRow(
                rs.getString("refund_request_id"), rs.getString("provider_refund_id"),
                rs.getString("status"), rs.getString("business_outcome"),
                rs.getString("execution_status"), rs.getString("provider_status"),
                rs.getString("raw_provider_status"),
                rs.getBigDecimal("amount").toPlainString(),
                rs.getString("currency"), rs.getTimestamp("received_at").toInstant()), businessOrderId);
    }

    private MockBusinessOrder requireRefundableOrder(String businessOrderId) {
        var order = orders.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        if (order.paymentMode() != MockPaymentMode.AIRWALLEX_SANDBOX
                || !"PAID".equals(order.businessStatus()) || order.paymentOrderId() == null) {
            throw new InvalidMockPaymentEventException("仅已支付的 Airwallex 沙盒订单可申请模拟退款");
        }
        return order;
    }

    private RefundRequestRow findRequest(String requestId) {
        return jdbcTemplate.query("""
                SELECT refund_request_id, business_order_id, amount, currency, reason,
                       business_decision, payment_service_status, business_outcome, provider_status,
                       raw_provider_status,
                       payment_refund_id,
                       created_at, decided_at, last_sent_at
                FROM mock_xingzhe_refund_request WHERE refund_request_id = ?
                """, (rs, rowNum) -> new RefundRequestRow(
                rs.getString("refund_request_id"), rs.getString("business_order_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("reason"), rs.getString("business_decision"),
                rs.getString("payment_service_status"), rs.getString("business_outcome"),
                rs.getString("provider_status"), rs.getString("raw_provider_status"),
                rs.getString("payment_refund_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getTimestamp("last_sent_at") == null ? null : rs.getTimestamp("last_sent_at").toInstant()),
                requestId).stream().findFirst().orElse(null);
    }

    private RefundRequestRow lockRequest(String requestId) {
        return jdbcTemplate.query("""
                SELECT refund_request_id, business_order_id, amount, currency, reason,
                       business_decision, payment_service_status, business_outcome, provider_status,
                       raw_provider_status,
                       payment_refund_id,
                       created_at, decided_at, last_sent_at
                FROM mock_xingzhe_refund_request WHERE refund_request_id = ? FOR UPDATE
                """, (rs, rowNum) -> new RefundRequestRow(
                rs.getString("refund_request_id"), rs.getString("business_order_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("reason"), rs.getString("business_decision"),
                rs.getString("payment_service_status"), rs.getString("business_outcome"),
                rs.getString("provider_status"), rs.getString("raw_provider_status"),
                rs.getString("payment_refund_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getTimestamp("last_sent_at") == null ? null : rs.getTimestamp("last_sent_at").toInstant()),
                requestId).stream().findFirst()
                .orElseThrow(() -> new InvalidMockPaymentEventException("模拟退款申请不存在"));
    }

    private void requireSameRequest(RefundRequestRow existing, String businessOrderId,
            BigDecimal amount, String currency, String reason) {
        if (!existing.businessOrderId().equals(businessOrderId)
                || new BigDecimal(existing.amount()).compareTo(amount) != 0
                || !existing.currency().equals(currency)
                || !java.util.Objects.equals(existing.reason(), reason)) {
            throw new InvalidMockPaymentEventException("退款申请编号已用于不同内容");
        }
    }

    private record ApprovedRequest(MockBusinessOrder order, RefundRequestRow request) {
    }

    public record RefundEventRow(
            String refundRequestId, String providerRefundId, String status,
            String businessOutcome, String executionStatus, String providerStatus, String rawProviderStatus,
            String amount, String currency, Instant receivedAt) {
    }
}
