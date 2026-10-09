package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.application.MockPaymentEventHistory;
import java.time.Instant;
import java.util.List;

public record MockPaymentHistoryResponse(List<EventRow> events) {
    public static MockPaymentHistoryResponse from(List<MockPaymentEventHistory> events) {
        return new MockPaymentHistoryResponse(events.stream().map(EventRow::from).toList());
    }

    public record EventRow(
            String eventId,
            String eventType,
            String paymentStatus,
            String amount,
            String currency,
            Instant occurredAt) {
        private static EventRow from(MockPaymentEventHistory event) {
            return new EventRow(event.eventId(), event.eventType(), event.paymentStatus(),
                    event.amount() == null ? null : event.amount().toPlainString(), event.currency(), event.occurredAt());
        }
    }
}
