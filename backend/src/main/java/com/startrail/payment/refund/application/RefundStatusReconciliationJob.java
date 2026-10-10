package com.startrail.payment.refund.application;

import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderRefund;
import com.startrail.payment.refund.infrastructure.RefundJdbcRepository;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundStatusReconciliationJob {
    private final RefundJdbcRepository refunds;
    private final PaymentOrderRepository paymentOrders;
    private final AirwallexRefundGateway airwallex;
    private final RefundStateService stateService;

    public RefundStatusReconciliationJob(RefundJdbcRepository refunds,
            PaymentOrderRepository paymentOrders, AirwallexRefundGateway airwallex,
            RefundStateService stateService) {
        this.refunds = refunds;
        this.paymentOrders = paymentOrders;
        this.airwallex = airwallex;
        this.stateService = stateService;
    }

    @Scheduled(fixedDelayString = "${refund.status-check-delay-ms:30000}")
    public void reconcileDueRefunds() {
        for (var id : refunds.dueIds()) {
            if (refunds.claimCheck(id)) {
                reconcile(id);
            }
        }
        for (var id : refunds.exhaustedIds()) {
            stateService.stopUnresolvedChecks(id);
        }
    }

    private void reconcile(UUID id) {
        var refund = refunds.findById(id).orElseThrow();
        var payment = paymentOrders.findById(refund.paymentOrderId()).orElseThrow();
        ProviderRefund providerRefund;
        try {
            if (refund.providerRefundId() == null) {
                providerRefund = airwallex.findByRequestId(
                        payment.providerPaymentIntentId(), refund.providerRequestId());
            } else {
                providerRefund = airwallex.retrieve(refund.providerRefundId());
            }
        } catch (RuntimeException exception) {
            // 仅将服务商查询失败记为服务商不可用；本地数据库错误应继续抛出并写入应用日志。
            refunds.recordCheckFailure(id, "Airwallex 退款查询暂不可用：" + exception.getClass().getSimpleName());
            return;
        }

        if (providerRefund == null) {
            stateService.markNeedsReview(id, "未查到退款记录，继续按服务商请求编号核实");
            return;
        }
        stateService.applyProviderResult(id, providerRefund);
    }
}
