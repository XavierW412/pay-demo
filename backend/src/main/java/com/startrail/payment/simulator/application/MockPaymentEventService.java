package com.startrail.payment.simulator.application;

import com.startrail.payment.integrations.startrail.IntegrationSignatureVerifier;
import com.startrail.payment.notification.PaymentStatusNotification;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.shared.application.StateChangePublisher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local")
public class MockPaymentEventService {
    private final IntegrationSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final String outboundSecret;
    private final StateChangePublisher eventHub;

    public MockPaymentEventService(
            IntegrationSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            @Value("${startrail.integration.outbound-secret}") String outboundSecret,
            StateChangePublisher eventHub) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.outboundSecret = outboundSecret;
        this.eventHub = eventHub;
    }

    @Transactional
    public void receive(String timestamp, String nonce, String signature, byte[] rawBody) {
        var rawPayload = new String(rawBody, StandardCharsets.UTF_8);
        signatureVerifier.verify(timestamp, nonce, signature, rawPayload, outboundSecret);
        final String eventType;
        final String businessOrderId;
        try {
            var event = objectMapper.readTree(rawPayload);
            eventType = event.path("eventType").asText();
            businessOrderId = event.path("businessOrderId").asText();
        } catch (JacksonException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙通知格式无效");
        }
        if ("refund.status_changed".equals(eventType)) {
            receiveRefund(rawPayload);
            eventHub.publishMockOrderChangeAfterCommit(businessOrderId);
            return;
        }
        if (!eventType.startsWith("payment_intent.") && !"PAYMENT_STATUS_CHANGED".equals(eventType)) {
            throw new InvalidMockPaymentEventException("模拟星辙通知事件类型无效");
        }
        receivePayment(rawPayload);
        eventHub.publishMockOrderChangeAfterCommit(businessOrderId);
    }

    private void receivePayment(String rawPayload) {
        final PaymentStatusNotification notification;
        try {
            notification = objectMapper.readValue(rawPayload, PaymentStatusNotification.class);
        } catch (JacksonException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知格式无效");
        }
        if (notification.eventId() == null || notification.businessOrderId() == null
                || notification.paymentOrderId() == null || notification.status() == null
                || notification.amount() == null || notification.currency() == null) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知缺少必要字段");
        }
        try {
            PaymentStatus.valueOf(notification.status());
        } catch (IllegalArgumentException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知包含未知状态");
        }

        var matchingOrders = jdbcTemplate.queryForList("""
                SELECT business_order_id FROM mock_xingzhe_order
                WHERE business_order_id = ? AND payment_order_id = ? AND amount = ? AND currency = ?
                """, String.class, notification.businessOrderId(), notification.paymentOrderId().toString(),
                notification.amount(), notification.currency());
        if (matchingOrders.isEmpty()) {
            throw new InvalidMockPaymentEventException("支付通知与模拟星辙订单信息不匹配");
        }

        var inserted = jdbcTemplate.update("""
                INSERT INTO mock_xingzhe_payment_event(
                    event_id, business_order_id, received_at, event_type, payment_order_id,
                    payment_status, amount, currency, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, notification.eventId(), notification.businessOrderId(),
                java.sql.Timestamp.from(Instant.now()), notification.eventType(),
                notification.paymentOrderId().toString(), notification.status(), notification.amount(),
                notification.currency(), java.sql.Timestamp.from(notification.occurredAt() == null
                        ? Instant.now() : notification.occurredAt()));
        if (inserted == 0) {
            return;
        }

        var occurredAt = notification.occurredAt() == null ? Instant.now() : notification.occurredAt();
        jdbcTemplate.update("""
                WITH incoming(status, occurred_at, event_id) AS (
                    VALUES (?, CAST(? AS TIMESTAMPTZ), ?)
                )
                UPDATE mock_xingzhe_order AS orders
                SET payment_status = incoming.status,
                    business_status = CASE WHEN incoming.status = 'SUCCEEDED' THEN 'PAID' ELSE orders.business_status END,
                    service_status = CASE WHEN incoming.status = 'SUCCEEDED' THEN 'ACTIVE' ELSE orders.service_status END,
                    updated_at = GREATEST(orders.updated_at, incoming.occurred_at)
                FROM incoming
                WHERE orders.business_order_id = ?
                    AND orders.business_status <> 'PAID'
                    AND (orders.payment_status <> 'CANCELLED' OR incoming.status = 'CANCELLED')
                    AND NOT EXISTS (
                        SELECT 1 FROM mock_xingzhe_payment_event AS newer_event
                        WHERE newer_event.business_order_id = orders.business_order_id
                            AND newer_event.event_id <> incoming.event_id
                            AND newer_event.occurred_at > incoming.occurred_at
                    )
                """, notification.status(), java.sql.Timestamp.from(occurredAt), notification.eventId(),
                notification.businessOrderId());
    }

    private void receiveRefund(String rawPayload) {
        final MockRefundNotification notification;
        try {
            notification = objectMapper.readValue(rawPayload, MockRefundNotification.class);
        } catch (JacksonException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙退款通知格式无效");
        }
        var executionStatus = notification.executionStatus() == null
                ? notification.status() : notification.executionStatus();
        var businessOutcome = notification.businessOutcome() == null
                ? legacyBusinessOutcome(executionStatus) : notification.businessOutcome();
        if (notification.eventId() == null || notification.refundRequestId() == null
                || notification.refundId() == null || notification.paymentOrderId() == null
                || notification.businessOrderId() == null || executionStatus == null
                || notification.amount() == null || notification.currency() == null
                || notification.amount().signum() <= 0) {
            throw new InvalidMockPaymentEventException("模拟星辙退款通知缺少必要字段");
        }
        if (!java.util.Set.of("PENDING_OPERATOR_CONFIRMATION", "BLOCKED", "SUBMITTING", "RECEIVED",
                "ACCEPTED", "SUCCEEDED", "FAILED", "NEEDS_REVIEW")
                .contains(executionStatus)) {
            throw new InvalidMockPaymentEventException("模拟星辙退款通知包含未知状态");
        }
        if (!java.util.Set.of("PROCESSING", "SUCCEEDED", "FAILED", "PENDING").contains(businessOutcome)
                || notification.status() != null && !notification.status().equals(executionStatus)
                || notification.providerStatus() != null && !java.util.Set.of(
                        "RECEIVED", "ACCEPTED", "SETTLED", "SUCCEEDED", "FAILED")
                        .contains(notification.providerStatus())
                || notification.rawProviderStatus() != null && !java.util.Set.of(
                        "RECEIVED", "ACCEPTED", "SETTLED", "SUCCEEDED", "FAILED")
                        .contains(notification.rawProviderStatus())
                || notification.rawProviderStatus() != null && notification.providerStatus() != null
                        && !normalizeLegacyProviderStatus(notification.rawProviderStatus())
                                .equals(notification.providerStatus())
                || !isValidRefundOutcome(executionStatus, businessOutcome, notification.providerStatus())) {
            throw new InvalidMockPaymentEventException("模拟星辙退款通知的业务结果与状态组合无效");
        }
        var matchingOrders = jdbcTemplate.queryForList("""
                SELECT business_order_id FROM mock_xingzhe_order
                WHERE business_order_id = ? AND payment_order_id = ? AND currency = ?
                """, String.class, notification.businessOrderId(), notification.paymentOrderId().toString(),
                notification.currency());
        if (matchingOrders.isEmpty()) {
            throw new InvalidMockPaymentEventException("退款通知与模拟星辙订单信息不匹配");
        }
        var matchingRequests = jdbcTemplate.queryForList("""
                SELECT refund_request_id FROM mock_xingzhe_refund_request
                WHERE refund_request_id = ? AND business_order_id = ? AND amount = ? AND currency = ?
                """, String.class, notification.refundRequestId(), notification.businessOrderId(),
                notification.amount(), notification.currency());
        if (matchingRequests.isEmpty()) {
            throw new InvalidMockPaymentEventException("退款通知与模拟星辙退款申请不匹配");
        }
        var occurredAt = notification.occurredAt() == null ? Instant.now() : notification.occurredAt();
        var inserted = jdbcTemplate.update("""
                INSERT INTO mock_xingzhe_refund_event(
                    event_id, business_order_id, refund_request_id, provider_refund_id,
                    status, business_outcome, execution_status, provider_status, raw_provider_status,
                    amount, currency, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, notification.eventId(), notification.businessOrderId(), notification.refundRequestId(),
                notification.providerRefundId(), executionStatus, businessOutcome, executionStatus,
                notification.providerStatus(), notification.rawProviderStatus(), notification.amount(),
                notification.currency(), java.sql.Timestamp.from(Instant.now()));
        if (inserted == 0) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE mock_xingzhe_refund_request
                SET payment_service_status = ?, payment_refund_id = ?, business_outcome = ?,
                    provider_status = ?, raw_provider_status = ?, last_event_at = ?
                WHERE refund_request_id = ? AND business_order_id = ?
                    AND (last_event_at IS NULL OR last_event_at < ?)
                """, executionStatus, notification.refundId(), businessOutcome,
                notification.providerStatus(), notification.rawProviderStatus(),
                java.sql.Timestamp.from(occurredAt), notification.refundRequestId(), notification.businessOrderId(),
                java.sql.Timestamp.from(occurredAt));
    }

    private static String legacyBusinessOutcome(String executionStatus) {
        if (executionStatus == null) return null;
        return switch (executionStatus) {
            case "ACCEPTED", "SUCCEEDED" -> "SUCCEEDED";
            case "RECEIVED", "SUBMITTING" -> "PROCESSING";
            case "FAILED" -> "FAILED";
            default -> "PENDING";
        };
    }

    private static boolean isValidRefundOutcome(String executionStatus, String businessOutcome,
            String providerStatus) {
        return switch (executionStatus) {
            case "ACCEPTED" -> "SUCCEEDED".equals(businessOutcome)
                    && (providerStatus == null || "ACCEPTED".equals(providerStatus));
            case "SUCCEEDED" -> "SUCCEEDED".equals(businessOutcome)
                    && (providerStatus == null || "SETTLED".equals(providerStatus)
                    || "SUCCEEDED".equals(providerStatus));
            case "RECEIVED", "SUBMITTING" -> "PROCESSING".equals(businessOutcome);
            case "FAILED" -> "FAILED".equals(businessOutcome);
            default -> "PENDING".equals(businessOutcome);
        };
    }

    private static String normalizeLegacyProviderStatus(String providerStatus) {
        return "SUCCEEDED".equals(providerStatus) ? "SETTLED" : providerStatus;
    }

    private record MockRefundNotification(
            String eventId, String eventType, String refundRequestId, UUID refundId,
            UUID paymentOrderId, String businessOrderId, String providerRefundId,
            String businessOutcome, String status, String executionStatus, String providerStatus,
            String rawProviderStatus, BigDecimal amount, String currency, Instant occurredAt) {
    }
}
