package com.startrail.payment.integrations.airwallex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "airwallex")
public record AirwallexProperties(
        String environment,
        String baseUrl,
        String clientId,
        String apiKey,
        String webhookSecret) {
}
