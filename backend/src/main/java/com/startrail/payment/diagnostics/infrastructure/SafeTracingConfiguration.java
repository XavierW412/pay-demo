package com.startrail.payment.diagnostics.infrastructure;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import java.io.IOException;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** 替换默认追踪处理器，避免原始业务异常进入跨度。 */
@Configuration
public class SafeTracingConfiguration {
    @Bean
    @Order(MicrometerTracingAutoConfiguration.DEFAULT_TRACING_OBSERVATION_HANDLER_ORDER)
    public DefaultTracingObservationHandler safeDefaultTracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer) {
            @Override
            public void onError(Observation.Context context) {
                recordSafeError(context, getRequiredSpan(context));
            }
        };
    }

    @Bean
    @Order(MicrometerTracingAutoConfiguration.RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER)
    public PropagatingReceiverTracingObservationHandler<?> safeReceiverTracingObservationHandler(
            Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<ReceiverContext<?>>(tracer, propagator) {
            @Override
            public void onError(ReceiverContext<?> context) {
                recordSafeError(context, getRequiredSpan(context));
            }
        };
    }

    @Bean
    @Order(MicrometerTracingAutoConfiguration.SENDER_TRACING_OBSERVATION_HANDLER_ORDER)
    public PropagatingSenderTracingObservationHandler<?> safeSenderTracingObservationHandler(
            Tracer tracer, Propagator propagator) {
        return new PropagatingSenderTracingObservationHandler<SenderContext<?>>(tracer, propagator) {
            @Override
            public void onError(SenderContext<?> context) {
                recordSafeError(context, getRequiredSpan(context));
            }
        };
    }

    static KeyValue exceptionKeyValue(Throwable error) {
        return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION,
                error == null ? KeyValue.NONE_VALUE : errorCategory(error));
    }

    private static void recordSafeError(Observation.Context context, Span span) {
        if (context.getError() == null) return;
        span.error(new SafeTraceException());
        span.tag("error.type", errorCategory(context.getError()));
    }

    private static String errorCategory(Throwable error) {
        if (error instanceof RestClientResponseException) return "HTTP_RESPONSE_ERROR";
        if (error instanceof ResourceAccessException || error instanceof IOException) return "IO_ERROR";
        if (error instanceof ResponseStatusException) return "HTTP_STATUS_ERROR";
        if (error instanceof SecurityException) return "SECURITY_ERROR";
        if (error instanceof IllegalArgumentException) return "INVALID_ARGUMENT";
        if (error instanceof IllegalStateException) return "INVALID_STATE";
        if (error instanceof Error) return "SYSTEM_ERROR";
        return "APPLICATION_ERROR";
    }

    private static final class SafeTraceException extends RuntimeException {
        private SafeTraceException() {
            super("技术异常", null, false, false);
        }
    }
}
