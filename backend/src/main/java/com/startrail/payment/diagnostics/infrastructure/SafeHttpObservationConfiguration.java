package com.startrail.payment.diagnostics.infrastructure;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

/** HTTP 跨度只保留受限路由和状态标签，不记录实际请求地址。 */
@Configuration
public class SafeHttpObservationConfiguration {
    @Bean
    public ServerRequestObservationConvention safeServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                return KeyValues.empty();
            }

            @Override
            protected KeyValue exception(ServerRequestObservationContext context) {
                return SafeTracingConfiguration.exceptionKeyValue(context.getError());
            }
        };
    }

    @Bean
    public ClientRequestObservationConvention safeClientRequestObservationConvention() {
        return new DefaultClientRequestObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) {
                return KeyValues.empty();
            }

            @Override
            protected KeyValue uri(ClientRequestObservationContext context) {
                return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI,
                        approvedRoute(context.getUriTemplate()));
            }

            @Override
            protected KeyValue clientName(ClientRequestObservationContext context) {
                String route = approvedRoute(context.getUriTemplate());
                String client = route.startsWith("/pa/") || route.equals("/authentication/login") ? "airwallex"
                        : route.contains("payment-events") || route.equals("/api/v1/integration/payment-orders")
                                ? "xingzhe" : "other";
                return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, client);
            }

            @Override
            protected KeyValue exception(ClientRequestObservationContext context) {
                return SafeTracingConfiguration.exceptionKeyValue(context.getError());
            }
        };
    }

    private static String approvedRoute(String template) {
        if (template == null) return "UNKNOWN";
        String path = template;
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int start = path.indexOf('/', scheme + 3);
            path = start < 0 ? "/" : path.substring(start);
        }
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int fragment = path.indexOf('#');
        if (fragment >= 0) path = path.substring(0, fragment);
        return switch (path) {
            case "/authentication/login", "/pa/payment_intents/create", "/pa/payment_intents/{id}",
                    "/pa/refunds/create", "/pa/refunds/{id}", "/pa/refunds",
                    "/api/v1/integration/payment-orders", "/api/v1/payment-events",
                    "/mock-xingzhe/api/v1/payment-events" -> path;
            default -> "UNKNOWN";
        };
    }
}
