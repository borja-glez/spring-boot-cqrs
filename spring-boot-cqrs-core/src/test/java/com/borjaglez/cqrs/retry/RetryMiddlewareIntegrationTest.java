package com.borjaglez.cqrs.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.observability.MicrometerBusObservability;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.query.spring.SpringQueryBus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RetryMiddlewareIntegrationTest {

  private MeterRegistry meterRegistry;
  private List<BusMiddleware> middlewares;
  private final List<Duration> sleeps = new ArrayList<>();

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    RetryMiddleware retry =
        RetryMiddleware.builder()
            .defaultPolicy(RetryPolicy.defaults())
            .sleeper(sleeps::add)
            .build();
    middlewares = new ArrayList<>(List.of(new MicrometerBusObservability(meterRegistry), retry));
    AnnotationAwareOrderComparator.sort(middlewares);
  }

  @Test
  void commandSucceedsOnThirdAttemptAndEachAttemptIsTimed() throws Exception {
    FlakyCommandHandler handler = new FlakyCommandHandler(2);
    CommandHandlerRegistry registry = new CommandHandlerRegistry();
    registry.register(
        TestCommand.class,
        handler,
        FlakyCommandHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false);
    SpringCommandBus bus = new SpringCommandBus(registry, middlewares);

    String result = bus.dispatchAndReceive(new TestCommand("data"));

    assertThat(result).isEqualTo("handled:data");
    assertThat(handler.calls).isEqualTo(3);
    assertThat(sleeps).hasSize(2);
    assertThat(timerCount("command", "error")).isEqualTo(2);
    assertThat(timerCount("command", "success")).isEqualTo(1);
  }

  @Test
  void queryReturnsResultOfLastAttempt() throws Exception {
    FlakyQueryHandler handler = new FlakyQueryHandler(2);
    QueryHandlerRegistry registry = new QueryHandlerRegistry();
    registry.register(
        TestQuery.class,
        handler,
        FlakyQueryHandler.class.getMethod("handle", TestQuery.class),
        "test.query");
    SpringQueryBus bus = new SpringQueryBus(registry, middlewares);

    String result = bus.ask(new TestQuery("q"));

    assertThat(result).isEqualTo("answer:q:3");
    assertThat(timerCount("query", "error")).isEqualTo(2);
    assertThat(timerCount("query", "success")).isEqualTo(1);
  }

  private long timerCount(String type, String outcome) {
    Timer timer =
        meterRegistry
            .find("cqrs.bus.dispatch")
            .tag("cqrs.type", type)
            .tag("cqrs.outcome", outcome)
            .timer();
    return timer == null ? 0 : timer.count();
  }

  public static class FlakyCommandHandler {
    private final int failures;
    int calls;

    FlakyCommandHandler(int failures) {
      this.failures = failures;
    }

    public String handle(TestCommand command) {
      if (++calls <= failures) {
        throw new IllegalStateException("optimistic lock conflict");
      }
      return "handled:" + command.getData();
    }
  }

  public static class FlakyQueryHandler {
    private final int failures;
    int calls;

    FlakyQueryHandler(int failures) {
      this.failures = failures;
    }

    public String handle(TestQuery query) {
      if (++calls <= failures) {
        throw new IllegalStateException("timeout");
      }
      return "answer:" + query.getData() + ":" + calls;
    }
  }
}
