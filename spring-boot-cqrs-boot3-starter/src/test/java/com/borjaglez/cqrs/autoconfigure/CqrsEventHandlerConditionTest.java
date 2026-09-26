package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

class CqrsEventHandlerConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class))
          .withUserConfiguration(ConditionalHandlersConfiguration.class);

  @Test
  void conditionsFilterHandlersAndResolveBeanReferences() {
    contextRunner.run(
        context -> {
          EventBus eventBus = context.getBean(EventBus.class);
          StatusHandlers handlers = context.getBean(StatusHandlers.class);

          eventBus.publish(new StatusChanged("PENDING"));
          eventBus.publish(new StatusChanged("CONFIRMED"));

          assertThat(handlers.all).containsExactly("PENDING", "CONFIRMED");
          assertThat(handlers.confirmed).containsExactly("CONFIRMED");
          assertThat(handlers.flagged).containsExactly("PENDING", "CONFIRMED");
        });
  }

  @Test
  void malformedConditionFailsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class))
        .withBean(MalformedHandler.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasStackTraceContaining("MalformedHandler.on(")
                    .hasStackTraceContaining("invalid condition 'status =='"));
  }

  public static class StatusChanged extends Event {
    private final String status;

    StatusChanged(String status) {
      this.status = status;
    }

    public String getStatus() {
      return status;
    }
  }

  public static class FeatureFlags {
    public boolean enabled(String feature) {
      return "notify".equals(feature);
    }
  }

  @EventHandler
  public static class StatusHandlers {
    final List<String> all = new ArrayList<>();
    final List<String> confirmed = new ArrayList<>();
    final List<String> flagged = new ArrayList<>();

    @HandleEvent
    public void onAny(StatusChanged event) {
      all.add(event.getStatus());
    }

    @HandleEvent(condition = "status == 'CONFIRMED'")
    public void onConfirmed(StatusChanged event) {
      confirmed.add(event.getStatus());
    }

    @HandleEvent(condition = "@featureFlags.enabled('notify') and #event.status != null")
    public void onFlagged(StatusChanged event) {
      flagged.add(event.getStatus());
    }
  }

  @EventHandler
  public static class MalformedHandler {
    @HandleEvent(condition = "status ==")
    public void on(StatusChanged event) {
      // never registered
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ConditionalHandlersConfiguration {
    @Bean
    FeatureFlags featureFlags() {
      return new FeatureFlags();
    }

    @Bean
    StatusHandlers statusHandlers() {
      return new StatusHandlers();
    }
  }
}
