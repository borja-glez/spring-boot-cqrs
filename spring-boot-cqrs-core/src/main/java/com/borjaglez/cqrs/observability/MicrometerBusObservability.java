package com.borjaglez.cqrs.observability;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.query.Query;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MicrometerBusObservability implements BusObservabilityInterceptor {

  private final MeterRegistry meterRegistry;

  public MicrometerBusObservability(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    String type = resolveType(message);
    String messageName = message.getClass().getSimpleName();

    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "success";
    try {
      return chain.proceed(message);
    } catch (Exception e) {
      outcome = "error";
      throw e;
    } finally {
      sample.stop(
          Timer.builder("cqrs.bus.dispatch")
              .tag("cqrs.type", type)
              .tag("cqrs.message", messageName)
              .tag("cqrs.outcome", outcome)
              .register(meterRegistry));
    }
  }

  private String resolveType(Object message) {
    if (message instanceof Command) {
      return "command";
    } else if (message instanceof Event) {
      return "event";
    } else if (message instanceof Query) {
      return "query";
    }
    return "unknown";
  }
}
