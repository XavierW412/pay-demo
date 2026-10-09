package com.startrail.payment.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionProfileGuard {
    public ProductionProfileGuard(
            @Value("${startrail.simulator.enabled:false}") boolean simulatorEnabled,
            @Value("${startrail.integration.notification-url}") String notificationUrl,
            @Value("${startrail.integration.inbound-secret}") String inboundSecret,
            @Value("${startrail.integration.refund-inbound-secret}") String refundInboundSecret,
            @Value("${startrail.integration.outbound-secret}") String outboundSecret) {
        if (simulatorEnabled) {
            throw new IllegalStateException("正式配置禁止启用本地星辙模拟器");
        }
        if (!notificationUrl.startsWith("https://") || inboundSecret.length() < 32
                || refundInboundSecret.length() < 32 || outboundSecret.length() < 32) {
            throw new IllegalStateException("正式环境需要 HTTPS 通知地址和至少 32 字符的支付、退款及出站签名密钥");
        }
    }
}
