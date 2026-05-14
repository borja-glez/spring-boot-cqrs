package com.borjaglez.cqrs.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.borjaglez.cqrs.context.MessageContext;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs")
public class CqrsProperties {
  private NamingProperties naming = new NamingProperties();
  private EventsProperties events = new EventsProperties();
  private ValidationProperties validation = new ValidationProperties();
  private ObservabilityProperties observability = new ObservabilityProperties();
  private IntrospectionProperties introspection = new IntrospectionProperties();
  private ContextProperties context = new ContextProperties();
  private TracingProperties tracing = new TracingProperties();

  @Getter
  @Setter
  public static class NamingProperties {
    private String prefix = "";
  }

  @Getter
  @Setter
  public static class EventsProperties {
    private boolean transactional = true;
  }

  @Getter
  @Setter
  public static class ValidationProperties {
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class ObservabilityProperties {
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class IntrospectionProperties {
    private boolean logHandlersOnStartup = false;
  }

  @Getter
  @Setter
  public static class ContextProperties {
    private boolean enabled = true;
    private boolean autoCorrelationId = true;
    private List<String> mdcKeys = new ArrayList<>(List.of(MessageContext.CORRELATION_ID_KEY));
    private String headerPrefix = "cqrs.context.";
  }

  @Getter
  @Setter
  public static class TracingProperties {
    private boolean enabled = true;
    private String observationName = "cqrs.bus.dispatch";
  }
}
