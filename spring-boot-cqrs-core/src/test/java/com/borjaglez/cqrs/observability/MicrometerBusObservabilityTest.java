package com.borjaglez.cqrs.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerBusObservabilityTest {

  private MeterRegistry meterRegistry;
  private MicrometerBusObservability observability;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    observability = new MicrometerBusObservability(meterRegistry);
  }

  @Test
  void timerRecordedForCommand() throws Exception {
    MiddlewareChain chain = msg -> "done";
    observability.process(new TestCommand("data"), chain);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.type", "command").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void timerRecordedForEvent() throws Exception {
    MiddlewareChain chain = msg -> null;
    observability.process(new TestEvent("data"), chain);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.type", "event").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void timerRecordedForQuery() throws Exception {
    MiddlewareChain chain = msg -> "answer";
    observability.process(new TestQuery("data"), chain);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.type", "query").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void timerRecordedWithSuccessOutcome() throws Exception {
    MiddlewareChain chain = msg -> "ok";
    observability.process(new TestCommand("data"), chain);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.outcome", "success").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void timerRecordedWithErrorOutcomeOnException() {
    MiddlewareChain chain =
        msg -> {
          throw new RuntimeException("fail");
        };

    assertThatThrownBy(() -> observability.process(new TestCommand("data"), chain))
        .isInstanceOf(RuntimeException.class);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.outcome", "error").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void unknownTypeForNonCqrsMessage() throws Exception {
    MiddlewareChain chain = msg -> null;
    observability.process("plain-string", chain);

    Timer timer = meterRegistry.find("cqrs.bus.dispatch").tag("cqrs.type", "unknown").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }
}
