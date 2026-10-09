package com.startrail.payment.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionProfileGuard {
    public ProductionProfileGuard(@Value("${startrail.simulator.enabled:false}") boolean simulatorEnabled) {
        if (simulatorEnabled) {
            throw new IllegalStateException("正式配置禁止启用本地星辙模拟器");
        }
    }
}
