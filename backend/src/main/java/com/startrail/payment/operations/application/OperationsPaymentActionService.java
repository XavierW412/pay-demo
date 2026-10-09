package com.startrail.payment.operations.application;

import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.application.PaymentStatusVerificationService;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsPaymentActionService {
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentStatusVerificationService statusVerificationService;
    private final OperatorAccountService accountService;
    private final OperatorAuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public OperationsPaymentActionService(
            PaymentOrderRepository paymentOrderRepository,
            PaymentStatusVerificationService statusVerificationService,
            OperatorAccountService accountService,
            OperatorAuditService auditService,
            JdbcTemplate jdbcTemplate) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.statusVerificationService = statusVerificationService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void refresh(String username, UUID paymentOrderId) {
        var paymentOrder = requireOrder(paymentOrderId);
        if (paymentOrder.provider() != PaymentProvider.AIRWALLEX) {
            auditService.record(username, paymentOrderId, "REFRESH_PROVIDER_STATUS", "REJECTED",
                    "本地模拟交易不支持查询 Airwallex");
            throw new InvalidOperatorRequestException("本地模拟交易不能查询 Airwallex 状态");
        }
        try {
            statusVerificationService.verifyByPaymentOrderId(paymentOrderId);
            auditService.record(username, paymentOrderId, "REFRESH_PROVIDER_STATUS", "SUCCEEDED", "已查询 Airwallex 当前状态");
        } catch (RuntimeException exception) {
            auditService.record(username, paymentOrderId, "REFRESH_PROVIDER_STATUS", "FAILED", "Airwallex 状态查询失败");
            throw exception;
        }
    }

    @Transactional
    public void retryNotification(String username, UUID paymentOrderId) {
        requireOrder(paymentOrderId);
        var latestEvent = jdbcTemplate.query("""
                SELECT event_id, delivery_status FROM notification_outbox
                WHERE payment_order_id = ? AND event_type LIKE 'payment_intent.%'
                ORDER BY created_at DESC, event_id DESC LIMIT 1
                """, (rs, rowNum) -> new OutboxEvent(
                rs.getString("event_id"), rs.getString("delivery_status")), paymentOrderId)
                .stream().findFirst().orElseThrow(() -> new InvalidOperatorRequestException("这笔交易尚无待重试通知"));
        if (!"RETRY".equals(latestEvent.deliveryStatus())) {
            throw new InvalidOperatorRequestException("最新通知当前不需要重试");
        }
        var updated = jdbcTemplate.update("""
                UPDATE notification_outbox SET delivery_status = 'PENDING',
                    next_attempt_at = now(), last_error = NULL
                WHERE event_id = ? AND delivery_status = 'RETRY'
                """, latestEvent.eventId());
        if (updated == 0) {
            throw new InvalidOperatorRequestException("通知状态已经改变，请刷新后再试");
        }
        auditService.record(username, paymentOrderId, "RETRY_NOTIFICATION", "SUCCEEDED", "重新安排通知星辙");
    }

    @Transactional
    public void addNote(String username, UUID paymentOrderId, String body) {
        requireOrder(paymentOrderId);
        var normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new InvalidOperatorRequestException("备注内容需为 1 至 1000 字");
        }
        jdbcTemplate.update("""
                INSERT INTO operator_note(payment_order_id, actor_id, body, created_at)
                VALUES (?, ?, ?, ?)
                """, paymentOrderId, accountService.requireId(username), normalized,
                java.sql.Timestamp.from(Instant.now()));
        auditService.record(username, paymentOrderId, "ADD_EXCEPTION_NOTE", "SUCCEEDED", "添加异常处理备注");
    }

    private PaymentOrder requireOrder(UUID paymentOrderId) {
        return paymentOrderRepository.findById(paymentOrderId)
                .orElseThrow(() -> new InvalidOperatorRequestException("支付单不存在"));
    }

    private record OutboxEvent(String eventId, String deliveryStatus) {
    }
}
