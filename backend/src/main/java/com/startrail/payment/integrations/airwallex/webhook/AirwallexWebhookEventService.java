package com.startrail.payment.integrations.airwallex.webhook;

import com.startrail.payment.observability.infrastructure.PaymentTelemetry;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentDetails;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentGateway;
import com.startrail.payment.notification.PaymentStatusOutboxWriter;
import com.startrail.payment.payment.application.PaymentOrderProviderUpdateTransaction;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AirwallexWebhookEventService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AirwallexPaymentIntentGateway paymentIntentGateway;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentOrderProviderUpdateTransaction updateTransaction;
    private final PaymentStatusOutboxWriter outboxWriter;
    private final PaymentTelemetry telemetry;

    public AirwallexWebhookEventService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AirwallexPaymentIntentGateway paymentIntentGateway,
            PaymentOrderRepository paymentOrderRepository,
            PaymentOrderProviderUpdateTransaction updateTransaction,
            PaymentStatusOutboxWriter outboxWriter,
            PaymentTelemetry telemetry) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentIntentGateway = paymentIntentGateway;
        this.paymentOrderRepository = paymentOrderRepository;
        this.updateTransaction = updateTransaction;
        this.outboxWriter = outboxWriter;
        this.telemetry = telemetry;
    }

    public void accept(byte[] rawBody) {
        try {
            telemetry.observe(PaymentTelemetry.Operation.WEBHOOK_RECEIVE, () -> acceptObserved(rawBody));
        } catch (RuntimeException exception) {
            telemetry.increment(PaymentTelemetry.CounterMetric.WEBHOOK_RECEIPT, PaymentTelemetry.Result.FAILURE);
            throw exception;
        }
    }

    private void acceptObserved(byte[] rawBody) {
        final WebhookEvent event;
        final String rawPayload = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        try {
            event = objectMapper.readValue(rawPayload, WebhookEvent.class);
        } catch (JacksonException exception) {
            throw new InvalidAirwallexWebhookException("Airwallex Webhook JSON 格式无效");
        }
        if (event.id() == null || event.id().isBlank() || event.name() == null || event.data() == null
                || event.data().object() == null || event.data().object().id() == null) {
            throw new InvalidAirwallexWebhookException("Airwallex Webhook 缺少必要字段");
        }
        var safePayload = writeJson(new StoredWebhookEvent(
                event.id(), event.name(), new StoredWebhookData(new StoredWebhookObject(event.data().object().id()))));
        var inserted = jdbcTemplate.update("""
                INSERT INTO provider_webhook_event(
                    event_id, event_name, safe_payload, processing_status, received_at, next_attempt_at
                ) VALUES (?, ?, ?, 'PENDING', ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, event.id(), event.name(), safePayload,
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        telemetry.increment(PaymentTelemetry.CounterMetric.WEBHOOK_RECEIPT,
                inserted == 1 ? PaymentTelemetry.Result.ACCEPTED : PaymentTelemetry.Result.DUPLICATE);
    }

    @Transactional
    public void process(String eventId) {
        try {
            telemetry.observe(PaymentTelemetry.Operation.WEBHOOK_PROCESSING, () -> processObserved(eventId));
            telemetry.increment(PaymentTelemetry.CounterMetric.WEBHOOK_PROCESSING, PaymentTelemetry.Result.PROCESSED);
        } catch (RuntimeException exception) {
            telemetry.increment(PaymentTelemetry.CounterMetric.WEBHOOK_PROCESSING, PaymentTelemetry.Result.FAILURE);
            throw exception;
        }
    }

    private void processObserved(String eventId) {
        var stored = jdbcTemplate.queryForMap(
                "SELECT event_name, safe_payload FROM provider_webhook_event WHERE event_id = ?", eventId);
        var eventName = (String) stored.get("event_name");
        final StoredWebhookEvent event;
        try {
            event = objectMapper.readValue((String) stored.get("safe_payload"), StoredWebhookEvent.class);
        } catch (JacksonException exception) {
            throw new InvalidAirwallexWebhookException("已存储的 Airwallex Webhook 无法解析");
        }
        if (!eventName.startsWith("payment_intent.")) {
            markProcessed(eventId);
            return;
        }

        var current = paymentIntentGateway.retrieve(event.data().object().id());
        var paymentOrder = paymentOrderRepository.findByProviderPaymentIntentId(current.id())
                .orElseThrow(() -> new IllegalStateException("Airwallex Payment Intent 尚未关联内部支付单"));
        var updated = updateTransaction.applyVerifiedProviderStatus(
                paymentOrder.id(), current.id(), current.status(), current.amount(), current.currency(),
                current.merchantOrderId());
        if (updated.statusChanged()) {
            outboxWriter.enqueue(event.id(), event.name(), updated.order());
        }
        markProcessed(eventId);
    }

    private void markProcessed(String eventId) {
        jdbcTemplate.update("""
                UPDATE provider_webhook_event SET processing_status = 'PROCESSED', processed_at = ?, last_error = NULL
                WHERE event_id = ?
                """, java.sql.Timestamp.from(Instant.now()), eventId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成星辙支付状态通知", exception);
        }
    }

    private record WebhookEvent(String id, String name, WebhookData data) {
    }

    private record WebhookData(AirwallexPaymentIntentDetails object) {
    }

    private record StoredWebhookEvent(String id, String name, StoredWebhookData data) {
    }

    private record StoredWebhookData(StoredWebhookObject object) {
    }

    private record StoredWebhookObject(String id) {
    }
}
