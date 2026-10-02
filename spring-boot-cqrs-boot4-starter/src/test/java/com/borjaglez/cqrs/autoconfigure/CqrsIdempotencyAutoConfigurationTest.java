package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotencyRegistrar;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

class CqrsIdempotencyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsIdempotencyAutoConfiguration.class));

  @Test
  void noStoreAndNoInvokerByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(IdempotencyStore.class);
          assertThat(context).doesNotHaveBean(IdempotentInvoker.class);
          assertThat(context).hasSingleBean(IdempotencyRegistrar.class);
        });
  }

  @Test
  void inMemoryStoreOnlyWhenRequested() {
    contextRunner
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InMemoryIdempotencyStore.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void userStoreGetsAnInvoker() {
    contextRunner
        .withUserConfiguration(UserStoreConfiguration.class)
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(InMemoryIdempotencyStore.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void idempotentHandlerWithoutStoreFailsStartup() {
    contextRunner
        .withUserConfiguration(IdempotentHandlerConfiguration.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasMessageContaining(
                        "are annotated with @Idempotent but no IdempotencyStore"));
  }

  @Test
  void idempotentCommandIsHandledOnce() {
    contextRunner
        .withUserConfiguration(IdempotentHandlerConfiguration.class)
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              CommandBus bus = context.getBean(CommandBus.class);
              Ship command = new Ship();
              bus.dispatch(command);
              bus.dispatch(command);
              assertThat(context.getBean(ShipHandler.class).calls).isEqualTo(1);
            });
  }

  @Test
  void bindsTheIdempotencyProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.idempotency.store=in-memory",
            "cqrs.idempotency.retention=2d",
            "cqrs.idempotency.in-memory.lease=30s")
        .run(
            context -> {
              CqrsProperties.IdempotencyProperties properties =
                  context.getBean(CqrsProperties.class).getIdempotency();
              assertThat(properties.getStore())
                  .isEqualTo(CqrsProperties.IdempotencyProperties.StoreType.IN_MEMORY);
              assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(2));
              assertThat(properties.getInMemory().getLease()).isEqualTo(Duration.ofSeconds(30));
            });
  }

  @Test
  void idempotencyDefaults() {
    CqrsProperties.IdempotencyProperties properties = new CqrsProperties().getIdempotency();

    assertThat(properties.getStore()).isNull();
    assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(7));
    assertThat(properties.getInMemory().getLease()).isEqualTo(Duration.ofMinutes(5));
  }

  @Configuration(proxyBeanMethods = false)
  static class UserStoreConfiguration {
    @Bean
    IdempotencyStore userStore() {
      return new IdempotencyStore() {
        @Override
        public Acquisition tryAcquire(String handlerId, String messageId) {
          return Acquisition.ACQUIRED;
        }

        @Override
        public void complete(String handlerId, String messageId) {}

        @Override
        public void release(String handlerId, String messageId) {}
      };
    }
  }

  public static class Ship extends Command {}

  @CommandHandler
  public static class ShipHandler {
    int calls;

    @HandleCommand
    @Idempotent
    public void handle(Ship command) {
      calls++;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class IdempotentHandlerConfiguration {
    @Bean
    ShipHandler shipHandler() {
      return new ShipHandler();
    }
  }
}
