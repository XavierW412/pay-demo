package com.startrail.payment.observability.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 支付链路的固定名称遥测；任何遥测异常都不得改变支付业务结果。 */
@Component
public class PaymentTelemetry {
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final int stalePendingMinutes;
    private final Map<QueueMetric, AtomicLong> queueValues = new EnumMap<>(QueueMetric.class);

    public PaymentTelemetry(
            MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry,
            JdbcTemplate jdbcTemplate,
            @Value("${payment.telemetry.stale-pending-minutes:15}") int stalePendingMinutes) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.stalePendingMinutes = Math.max(1, stalePendingMinutes);
        for (var metric : QueueMetric.values()) {
            var value = new AtomicLong();
            queueValues.put(metric, value);
            try {
                Gauge.builder(metric.meterName, value, AtomicLong::get)
                        .description(metric.description)
                        .register(meterRegistry);
            } catch (RuntimeException ignored) {
                // A registry that cannot initialize must not prevent the payment service from starting.
            }
        }
    }

    public <T> T observe(Operation operation, Supplier<T> action) {
        var startedAt = System.nanoTime();
        Observation observation = null;
        try {
            observation = Observation.createNotStarted(operation.observationName, observationRegistry);
            var requestId = MDC.get("requestId");
            if (requestId != null && requestId.matches("[0-9a-fA-F-]{36}")) {
                observation.highCardinalityKeyValue("request.id", requestId);
            }
            observation.lowCardinalityKeyValue("result", "success");
            observation.start();
        } catch (RuntimeException ignored) {
            // Continue without observation if the registry or diagnostic context is unavailable.
            observation = null;
        }

        try {
            if (observation == null) {
                return action.get();
            }
            Observation.Scope scope;
            try {
                scope = observation.openScope();
            } catch (RuntimeException ignored) {
                return action.get();
            }
            try {
                return action.get();
            } finally {
                try {
                    scope.close();
                } catch (RuntimeException ignored) {
                    // Closing an observation scope must not replace the original application outcome.
                }
            }
        } catch (RuntimeException | Error failure) {
            if (observation != null) {
                try {
                    observation.lowCardinalityKeyValue("result", "failure");
                } catch (RuntimeException ignored) {
                    // Fixed result marker is best effort and never replaces the original failure.
                }
            }
            throw failure;
        } finally {
            try {
                if (observation != null) {
                    observation.stop();
                }
            } catch (RuntimeException ignored) {
                // Monitoring must never change a payment, refund, or notification result.
            }
            try {
                Timer.builder("startrail.payment.operation.duration")
                        .tag("operation", operation.metricValue)
                        .register(meterRegistry)
                        .record(System.nanoTime() - startedAt, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (RuntimeException ignored) {
                // Meter registration/export failures must not replace the original application behavior.
            }
        }
    }

    public void observe(Operation operation, Runnable action) {
        observe(operation, () -> {
            action.run();
            return null;
        });
    }

    public void increment(CounterMetric metric, Result result) {
        try {
            Counter.builder(metric.meterName)
                    .description(metric.description)
                    .tag("result", result.metricValue)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException ignored) {
            // Meter registration/export failures must not replace the original application behavior.
        }
    }

    /** Refreshes bounded aggregate counts outside request and payment processing paths. */
    @Scheduled(fixedDelayString = "${payment.telemetry.queue-refresh-delay-ms:60000}", initialDelay = 10000)
    public void refreshQueueMetrics() {
        try {
            set(QueueMetric.PAYMENT_AWAITING_VERIFICATION, count("""
                    SELECT count(*) FROM payment_order
                    WHERE payment_provider = 'AIRWALLEX'
                      AND status IN ('CREATING', 'PROCESSING', 'REQUIRES_REVIEW')
                    """));
            set(QueueMetric.STALE_PENDING_PAYMENT_VERIFICATION, countStalePendingPayments());
            set(QueueMetric.NOTIFICATION_OUTBOX_PENDING, count("""
                    SELECT count(*) FROM notification_outbox
                    WHERE delivery_status IN ('PENDING', 'RETRY')
                    """));
            set(QueueMetric.REFUND_RESULT_UNKNOWN, count("""
                    SELECT count(*) FROM refund_order
                    WHERE status IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW')
                    """));
            set(QueueMetric.RECONCILIATION_DIFFERENCES, count("""
                    SELECT count(*) FROM payment_reconciliation_check c
                    WHERE c.outcome = 'DIFFERENCE'
                      AND c.checked_at >= now() - INTERVAL '24 hours'
                    """));
        } catch (RuntimeException ignored) {
            // Keep the last known values and let the scheduled refresh retry later.
        }
    }

    private long count(String sql) {
        var result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }

    private long countStalePendingPayments() {
        var result = jdbcTemplate.queryForObject("""
                -- updated_at tracks the latest persisted local payment record change, including provider status changes.
                SELECT count(*) FROM payment_order
                WHERE payment_provider = 'AIRWALLEX'
                  AND status IN ('CREATING', 'PROCESSING', 'REQUIRES_REVIEW')
                  AND updated_at <= now() - (? * INTERVAL '1 minute')
                """, Long.class, stalePendingMinutes);
        return result == null ? 0 : result;
    }

    private void set(QueueMetric metric, long value) {
        queueValues.get(metric).set(Math.max(0, value));
    }

    public enum Operation {
        PAYMENT_INTENT_CREATION("payment.intent.create", "payment_intent_creation"),
        PAYMENT_STATUS_VERIFICATION("payment.status.verify", "payment_status_verification"),
        WEBHOOK_RECEIVE("payment.webhook.receive", "webhook_receive"),
        WEBHOOK_PROCESSING("payment.webhook.process", "webhook_processing"),
        XINGZHE_NOTIFICATION_DELIVERY("startrail.notification.deliver", "startrail_notification_delivery"),
        REFUND_EXECUTION("refund.execute", "refund_execution"),
        REFUND_STATUS_VERIFICATION("refund.status.verify", "refund_status_verification"),
        PAYMENT_RECONCILIATION("payment.reconciliation.run", "payment_reconciliation");

        private final String observationName;
        private final String metricValue;

        Operation(String observationName, String metricValue) {
            this.observationName = observationName;
            this.metricValue = metricValue;
        }
    }

    public enum CounterMetric {
        PAYMENT_INTENT_CREATION("startrail.payment.intent.creation", "支付收款请求创建结果"),
        PAYMENT_VERIFICATION("startrail.payment.verification", "支付状态核查结果"),
        WEBHOOK_RECEIPT("startrail.payment.webhook.receipt", "Webhook 接收结果"),
        WEBHOOK_PROCESSING("startrail.payment.webhook.processing", "Webhook 处理结果"),
        OUTBOX_DELIVERY("startrail.payment.outbox.delivery", "星辙通知投递结果"),
        OUTBOX_STATE_WRITE("startrail.payment.outbox.state_write", "星辙通知状态写入结果"),
        REFUND_EXECUTION("startrail.payment.refund.execution", "退款执行结果"),
        REFUND_VERIFICATION("startrail.payment.refund.verification", "退款状态核查结果"),
        PAYMENT_RECONCILIATION("startrail.payment.reconciliation", "支付对账任务结果");

        private final String meterName;
        private final String description;

        CounterMetric(String meterName, String description) {
            this.meterName = meterName;
            this.description = description;
        }
    }

    public enum Result {
        SUCCESS("success"),
        SKIPPED("skipped"),
        DUPLICATE("duplicate"),
        FAILURE("failure"),
        UNKNOWN("unknown"),
        DELIVERED("delivered"),
        RETRY("retry"),
        ACCEPTED("accepted"),
        REJECTED("rejected"),
        PROCESSED("processed"),
        VERIFIED("verified"),
        IGNORED("ignored");

        private final String metricValue;

        Result(String metricValue) {
            this.metricValue = metricValue;
        }
    }

    private enum QueueMetric {
        PAYMENT_AWAITING_VERIFICATION(
                "startrail.payment.verification.pending",
                "等待服务端核实的支付数量"),
        STALE_PENDING_PAYMENT_VERIFICATION(
                "startrail.payment.verification.stale_pending",
                "本地支付状态记录长时间未变化且仍需处理或核查的支付数量"),
        NOTIFICATION_OUTBOX_PENDING(
                "startrail.payment.notification.outbox.pending",
                "待投递或重试的星辙通知数量"),
        REFUND_RESULT_UNKNOWN(
                "startrail.payment.refund.result.unknown",
                "等待 Airwallex 核实结果的退款数量"),
        RECONCILIATION_DIFFERENCES(
                "startrail.payment.reconciliation.differences.24h",
                "最近 24 小时的对账差异记录数");

        private final String meterName;
        private final String description;

        QueueMetric(String meterName, String description) {
            this.meterName = meterName;
            this.description = description;
        }
    }
}
