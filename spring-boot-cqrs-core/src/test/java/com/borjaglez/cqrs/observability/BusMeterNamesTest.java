package com.borjaglez.cqrs.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.tracing.TracingMiddleware;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

/**
 * Bus metrics and the dispatch observation together, as an application with Prometheus has them:
 * Micrometer derives a timer from the observation, and Prometheus rejects a second meter with the
 * same name and other tags, so both must have their own name (finding C14).
 */
class BusMeterNamesTest {

  @Test
  void busMetricsAndTheDispatchObservationBothReachPrometheus() throws Exception {
    PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    ObservationRegistry observations = ObservationRegistry.create();
    observations
        .observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(prometheus));
    MicrometerBusObservability metrics = new MicrometerBusObservability(prometheus);
    TracingMiddleware tracing = new TracingMiddleware(observations, null);
    MiddlewareChain handler = message -> "handled";

    tracing.process(new TestCommand("x"), message -> metrics.process(message, handler));

    assertThat(prometheus.scrape())
        .contains("cqrs_bus_dispatch_seconds_count{cqrs_message=\"TestCommand\"")
        .contains("cqrs_bus_handle_seconds_count{");
  }
}
