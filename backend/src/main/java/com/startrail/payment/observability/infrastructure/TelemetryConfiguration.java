package com.startrail.payment.observability.infrastructure;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 固定支付遥测指标的标签上限，防止未来改动引入高基数标签。 */
@Configuration
public class TelemetryConfiguration {
    @Bean
    public MeterFilter paymentTelemetryCardinalityGuard() {
        return MeterFilter.maximumAllowableTags(
                "startrail.payment.operation.duration", "operation", 8, MeterFilter.deny());
    }
}
