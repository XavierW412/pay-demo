package com.startrail.payment.integrations.airwallex.webhook;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class AirwallexWebhookEventProcessor {
    private final AirwallexWebhookEventService eventService;
    private final JdbcTemplate jdbcTemplate;

    public AirwallexWebhookEventProcessor(AirwallexWebhookEventService eventService, JdbcTemplate jdbcTemplate) {
        this.eventService = eventService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${payment.webhook-processor-delay-ms:3000}")
    public void processPendingEvents() {
        var eventIds = jdbcTemplate.query("""
                SELECT event_id FROM provider_webhook_event
                WHERE processing_status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?
                ORDER BY received_at LIMIT 20
                """, (rs, row) -> rs.getString("event_id"), java.sql.Timestamp.from(java.time.Instant.now()));
        for (var eventId : eventIds) {
            try {
                eventService.process(eventId);
            } catch (RuntimeException exception) {
                var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                jdbcTemplate.update("""
                        UPDATE provider_webhook_event SET processing_status = 'RETRY',
                            attempt_count = attempt_count + 1, last_error = ?,
                            next_attempt_at = ? WHERE event_id = ?
                        """, message.substring(0, Math.min(message.length(), 1000)),
                        java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(15)), eventId);
            }
        }
    }
}
