package com.checkout.payment.gateway.configuration;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed tracing configuration using Micrometer Observation.
 * 
 * Traces flow across:
 * - Controller → Service → Client → Bank
 * - Each observation captures timing, tags, and errors
 * - Exported to Jaeger for visualization
 */
@Configuration
public class TracingConfiguration {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
