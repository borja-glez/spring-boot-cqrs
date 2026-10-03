package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.fixtures.TestRecordingEventBus;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

@ExtendWith(OutputCaptureExtension.class)
class CqrsJdbcOutboxAutoConfigurationTest {

  /** A transport bus registered after the core auto-configuration, like kafkaEventBus. */
  @AutoConfiguration(afterName = "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration")
  static class RemoteBusConfiguration {
    @Bean
    TestRecordingEventBus remoteEventBus() {
      return new TestRecordingEventBus();
    }
  }

  @AutoConfiguration(afterName = "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration")
  static class SecondRemoteBusConfiguration {
    @Bean
    TestRecordingEventBus otherEventBus() {
      return new TestRecordingEventBus();
    }
  }

  @AutoConfiguration(afterName = "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration")
  static class RabbitBusConfiguration {
    @Bean
    RabbitMqEventBus rabbitMqEventBus() {
      return new RabbitMqEventBus(
          mock(RabbitMqPublisher.class),
          mock(RabbitMqNamingStrategy.class),
          mock(MessageNamingStrategy.class),
          "events");
    }
  }

  private final ApplicationContextRunner base =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.datasource.generate-unique-name=true", "cqrs.outbox.relay.interval=1h")
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class,
                  DataSourceTransactionManagerAutoConfiguration.class,
                  JdbcTemplateAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  CqrsAutoConfiguration.class,
                  CqrsSerializationAutoConfiguration.class,
                  CqrsJdbcOutboxAutoConfiguration.class));

  private final ApplicationContextRunner enabled =
      base.withPropertyValues("cqrs.outbox.enabled=true")
          .withConfiguration(AutoConfigurations.of(RemoteBusConfiguration.class));

  @Test
  void disabledByDefault() {
    base.withConfiguration(AutoConfigurations.of(RemoteBusConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(OutboxEventBus.class);
              assertThat(context).doesNotHaveBean(OutboxRelay.class);
            });
  }

  @Test
  void enabledRegistersTheOutboxAndKeepsTheLocalBusPrimary() {
    enabled.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(OutboxEventBus.class);
          assertThat(context).hasSingleBean(OutboxRelayScheduler.class);
          assertThat(context).hasSingleBean(OutboxCleanup.class);
          assertThat(context.getBean(EventBus.class)).isNotInstanceOf(OutboxEventBus.class);
          assertThat(context.getBean(OutboxRelay.class).target())
              .isSameAs(context.getBean("remoteEventBus"));
          assertThat(context.getBean(OutboxCleanup.class).retention())
              .isEqualTo(Duration.ofDays(7));
          assertThat(context.getBean(OutboxTracing.class)).isSameAs(OutboxTracing.noop());
          assertThat(
                  context
                      .getBean(JdbcTemplate.class)
                      .queryForObject("SELECT COUNT(*) FROM cqrs_outbox", Integer.class))
              .isZero();
        });
  }

  @Test
  void storedEventsAreRelayedToTheTargetBus() {
    enabled.run(
        context -> {
          TestOrderPlaced event = new TestOrderPlaced("o-1");
          new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
              .executeWithoutResult(s -> context.getBean(OutboxEventBus.class).publish(event));

          context.getBean(OutboxRelay.class).relayBatch();

          assertThat(context.getBean("remoteEventBus", TestRecordingEventBus.class).published())
              .extracting(e -> e.getEventId())
              .containsExactly(event.getEventId());
        });
  }

  @Test
  void propertiesConfigureTheTableTheRetentionAndTheTarget() {
    enabled
        .withConfiguration(AutoConfigurations.of(SecondRemoteBusConfiguration.class))
        .withPropertyValues(
            "cqrs.jdbc.outbox.table-name=app_outbox",
            "cqrs.outbox.retention=2d",
            "cqrs.outbox.relay.event-bus=otherEventBus")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(OutboxRelay.class).target())
                  .isSameAs(context.getBean("otherEventBus"));
              assertThat(context.getBean(OutboxCleanup.class).retention())
                  .isEqualTo(Duration.ofDays(2));
              assertThat(
                      context
                          .getBean(JdbcTemplate.class)
                          .queryForObject("SELECT COUNT(*) FROM app_outbox", Integer.class))
                  .isZero();
            });
  }

  @Test
  void noCandidateBusFailsWithAClearMessage() {
    base.withPropertyValues("cqrs.outbox.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("cqrs.outbox.relay.event-bus"));
  }

  @Test
  void severalCandidateBusesFailWithAClearMessage() {
    enabled
        .withConfiguration(AutoConfigurations.of(SecondRemoteBusConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("otherEventBus")
                    .hasMessageContaining("remoteEventBus"));
  }

  @Test
  void invalidRelayLimitsFailAtStartup() {
    enabled
        .withPropertyValues("cqrs.outbox.relay.batch-size=0")
        .run(
            context ->
                assertThat(context).getFailure().rootCause().hasMessageContaining("batch-size"));
  }

  @Test
  void writeOnlyInstanceHasNoRelay() {
    base.withPropertyValues("cqrs.outbox.enabled=true", "cqrs.outbox.relay.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxEventBus.class);
              assertThat(context).doesNotHaveBean(OutboxRelay.class);
              assertThat(context).doesNotHaveBean(OutboxRelayScheduler.class);
            });
  }

  @Test
  void cleanupCanBeDisabled() {
    enabled
        .withPropertyValues("cqrs.jdbc.outbox.cleanup-enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(OutboxCleanup.class));
  }

  @Test
  void userBeansReplaceTheDefaults() {
    OutboxEventBus custom = mock(OutboxEventBus.class);
    enabled
        .withBean("customOutbox", OutboxEventBus.class, () -> custom)
        .run(context -> assertThat(context.getBean(OutboxEventBus.class)).isSameAs(custom));
  }

  @Test
  void rabbitMqTargetWithoutConfirmsLogsAWarning(CapturedOutput output) {
    base.withPropertyValues("cqrs.outbox.enabled=true")
        .withConfiguration(AutoConfigurations.of(RabbitBusConfiguration.class))
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("publisher confirms");
  }

  @Test
  void rabbitMqTargetWithConfirmsDoesNotWarn(CapturedOutput output) {
    base.withPropertyValues(
            "cqrs.outbox.enabled=true", "cqrs.rabbitmq.events.confirms.enabled=true")
        .withConfiguration(AutoConfigurations.of(RabbitBusConfiguration.class))
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain("publisher confirms");
  }

  @Test
  void micrometerTracingIsUsedWhenATracerAndAPropagatorExist() {
    enabled
        .withBean(Tracer.class, () -> mock(Tracer.class))
        .withBean(Propagator.class, () -> mock(Propagator.class))
        .run(
            context ->
                assertThat(context.getBean(OutboxTracing.class))
                    .isInstanceOf(MicrometerOutboxTracing.class));
  }

  @Test
  void aTracerWithoutAPropagatorDoesNotTrace() {
    enabled
        .withBean(Tracer.class, () -> mock(Tracer.class))
        .run(
            context ->
                assertThat(context.getBean(OutboxTracing.class)).isSameAs(OutboxTracing.noop()));
  }

  @Test
  void withoutMicrometerTracingOnTheClasspathTheOutboxDoesNotTrace() {
    enabled
        .withClassLoader(new FilteredClassLoader(Tracer.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(OutboxTracing.class)).isSameAs(OutboxTracing.noop());
            });
  }
}
