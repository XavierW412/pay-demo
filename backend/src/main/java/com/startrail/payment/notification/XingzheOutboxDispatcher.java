package com.startrail.payment.notification;

import com.startrail.payment.integrations.startrail.IntegrationSigner;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class XingzheOutboxDispatcher {
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final IntegrationSigner signer;
    private final String outboundSecret;
    private final String notificationUrl;

    public XingzheOutboxDispatcher(
            JdbcTemplate jdbcTemplate,
            RestClient.Builder restClientBuilder,
            IntegrationSigner signer,
            @Value("${startrail.integration.outbound-secret}") String outboundSecret,
            @Value("${startrail.integration.notification-url}") String notificationUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = restClientBuilder.build();
        this.signer = signer;
        this.outboundSecret = outboundSecret;
        this.notificationUrl = notificationUrl;
    }

    @Scheduled(fixedDelayString = "${payment.notification-dispatch-delay-ms:3000}")
    public void dispatchPending() {
        var events = jdbcTemplate.query("""
                SELECT event_id, payload FROM notification_outbox
                WHERE delivery_status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?
                ORDER BY created_at LIMIT 20
                """, (rs, row) -> new OutboxRow(rs.getString("event_id"), rs.getString("payload")),
                java.sql.Timestamp.from(Instant.now()));
        for (var event : events) {
            deliver(event);
        }
    }

    private void deliver(OutboxRow event) {
        try {
            var timestamp = Long.toString(Instant.now().getEpochSecond());
            var nonce = UUID.randomUUID().toString();
            var signature = signer.sign(timestamp, nonce, event.payload(), outboundSecret);
            restClient.post()
                    .uri(notificationUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Startrail-Timestamp", timestamp)
                    .header("X-Startrail-Nonce", nonce)
                    .header("X-Startrail-Signature", signature)
                    .body(event.payload())
                    .retrieve()
                    .toBodilessEntity();
            jdbcTemplate.update("""
                    UPDATE notification_outbox SET delivery_status = 'DELIVERED', delivered_at = ?, last_error = NULL
                    WHERE event_id = ?
                    """, java.sql.Timestamp.from(Instant.now()), event.eventId());
        } catch (RuntimeException exception) {
            var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            jdbcTemplate.update("""
                    UPDATE notification_outbox SET delivery_status = 'RETRY', attempt_count = attempt_count + 1,
                        next_attempt_at = now() + make_interval(secs =>
                            LEAST(900.0, 30.0 * power(2, LEAST(attempt_count, 4)))::double precision),
                        last_error = ? WHERE event_id = ?
                    """,
                    message.substring(0, Math.min(message.length(), 1000)),
                    event.eventId());
        }
    }

    private record OutboxRow(String eventId, String payload) {
    }
}
