package com.startrail.payment.shared.application;

public interface StateChangePublisher {
    void publishOperationsChangeAfterCommit();

    void publishMockOrderChangeAfterCommit(String businessOrderId);
}
