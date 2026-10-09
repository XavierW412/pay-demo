package com.startrail.payment.integrations.airwallex.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AirwallexProperties.class)
public class AirwallexConfiguration {
}
