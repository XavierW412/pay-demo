package com.startrail.payment.reconciliation.application;

import com.startrail.payment.operations.application.InvalidOperatorRequestException;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.reconciliation.infrastructure.ReconciliationStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationQueryService {
    private final PaymentOrderRepository paymentOrders;
    private final ReconciliationStore store;

    public ReconciliationQueryService(PaymentOrderRepository paymentOrders, ReconciliationStore store) {
        this.paymentOrders = paymentOrders;
        this.store = store;
    }

    public List<ReconciliationSnapshot> history(UUID paymentOrderId, int limit, int offset) {
        validatePage(limit, offset);
        if (paymentOrders.findById(paymentOrderId).isEmpty()) {
            throw new InvalidOperatorRequestException("支付单不存在");
        }
        return store.history(paymentOrderId, limit, offset);
    }

    public List<ReconciliationSnapshot> latestDifferences(int limit, int offset) {
        validatePage(limit, offset);
        return store.latestDifferences(limit, offset);
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new InvalidOperatorRequestException("核查分页参数无效");
        }
    }
}
