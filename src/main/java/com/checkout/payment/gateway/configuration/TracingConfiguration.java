package com.checkout.payment.gateway.configuration;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Distributed tracing configuration using OpenTelemetry.
 * 
 * Traces flow across:
 * - Controller → Service → Client → Bank
 * - Each span captures timing, tags, and errors
 * - Exported to Jaeger for visualization
 */
@Configuration
public class TracingConfiguration {

    @Bean
    public ServerHttpObservationFilter observationFilter() {
        return new ServerHttpObservationFilter();
    }
}
