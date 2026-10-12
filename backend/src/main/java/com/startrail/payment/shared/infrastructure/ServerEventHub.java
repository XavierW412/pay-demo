package com.startrail.payment.shared.infrastructure;

import com.startrail.payment.shared.application.StateChangePublisher;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ServerEventHub implements StateChangePublisher {
    public static final String OPERATIONS_TOPIC = "operations";
    private static final String MOCK_ORDER_TOPIC_PREFIX = "mock-order:";
    private static final long CONNECTION_TIMEOUT_MILLIS = 0L;

    private final ConcurrentHashMap<String, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String topic) {
        var emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);
        subscribers.computeIfAbsent(topic, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(topic, emitter));
        emitter.onTimeout(() -> remove(topic, emitter));
        emitter.onError(ignored -> remove(topic, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ready"));
        } catch (IOException | IllegalStateException connectionFailure) {
            emitter.completeWithError(connectionFailure);
            remove(topic, emitter);
        }
        return emitter;
    }

    @Override
    public void publishOperationsChangeAfterCommit() {
        publishAfterCommit(OPERATIONS_TOPIC);
    }

    @Override
    public void publishMockOrderChangeAfterCommit(String businessOrderId) {
        if (businessOrderId != null && !businessOrderId.isBlank()) {
            publishAfterCommit(MOCK_ORDER_TOPIC_PREFIX + businessOrderId);
        }
    }

    private void publishAfterCommit(String topic) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(topic);
                }
            });
            return;
        }
        publish(topic);
    }

    private void publish(String topic) {
        var topicSubscribers = subscribers.get(topic);
        if (topicSubscribers == null) {
            return;
        }
        for (var emitter : topicSubscribers) {
            try {
                emitter.send(SseEmitter.event().name("state-changed").data("refresh"));
            } catch (IOException | IllegalStateException sendFailure) {
                emitter.completeWithError(sendFailure);
                remove(topic, emitter);
            }
        }
    }

    private void remove(String topic, SseEmitter emitter) {
        subscribers.computeIfPresent(topic, (ignored, topicSubscribers) -> {
            topicSubscribers.remove(emitter);
            return topicSubscribers.isEmpty() ? null : topicSubscribers;
        });
    }
}
