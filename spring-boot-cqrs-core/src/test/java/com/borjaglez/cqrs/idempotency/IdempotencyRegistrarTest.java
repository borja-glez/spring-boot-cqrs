package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.IdempotentEventHandlers;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;

class IdempotencyRegistrarTest {

  private final CommandHandlerRegistry commands = new CommandHandlerRegistry();
  private final EventHandlerRegistry events = new EventHandlerRegistry();

  private void registerIdempotentEventHandler() throws Exception {
    events.register(
        TestEvent.class,
        new IdempotentEventHandlers(),
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event",
        null,
        null,
        true,
        "projector#first");
  }

  private void registerIdempotentCommandHandler() throws Exception {
    commands.register(
        TestCommand.class,
        new TestCommandHandler(),
        TestCommandHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");
  }

  @Test
  void setsTheInvokerOnBothRegistries() throws Exception {
    registerIdempotentEventHandler();
    IdempotentEventHandlers handlers =
        (IdempotentEventHandlers) events.getHandlerInfos(TestEvent.class).get(0).bean();
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));

    new IdempotencyRegistrar(commands, events, () -> invoker).afterSingletonsInstantiated();
    TestEvent event = new TestEvent("data");
    events.handle(event);
    events.handle(event);

    assertThat(handlers.firstCalls).hasValue(1);
  }

  @Test
  void setsTheInvokerOnTheCommandRegistry() throws Exception {
    registerIdempotentCommandHandler();
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));

    new IdempotencyRegistrar(commands, events, () -> invoker).afterSingletonsInstantiated();

    assertThatCode(() -> commands.handle(new TestCommand("data"))).doesNotThrowAnyException();
  }

  @Test
  void failsWhenIdempotentHandlersHaveNoStore() throws Exception {
    registerIdempotentEventHandler();
    registerIdempotentCommandHandler();

    assertThatThrownBy(
            () ->
                new IdempotencyRegistrar(commands, events, () -> null)
                    .afterSingletonsInstantiated())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Handlers [orders#handle, projector#first] are annotated with @Idempotent but no"
                + " IdempotencyStore bean is configured; add spring-boot-cqrs-jdbc with a"
                + " DataSource (the JDBC store needs a single DataSource and a single, or"
                + " @Primary, PlatformTransactionManager), set cqrs.idempotency.store=in-memory,"
                + " or define an IdempotencyStore bean");
  }

  @Test
  void acceptsMissingStoreWhenNoHandlerIsIdempotent() throws Exception {
    commands.register(
        TestCommand.class,
        new TestCommandHandler(),
        TestCommandHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false);
    events.register(
        TestEvent.class,
        new IdempotentEventHandlers(),
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event");

    assertThatCode(
            () ->
                new IdempotencyRegistrar(commands, events, () -> null)
                    .afterSingletonsInstantiated())
        .doesNotThrowAnyException();
  }

  @Test
  void resolvesTheInvokerLazily() throws Exception {
    registerIdempotentEventHandler();
    AtomicBoolean asked = new AtomicBoolean();
    IdempotencyRegistrar registrar =
        new IdempotencyRegistrar(
            commands,
            events,
            () -> {
              asked.set(true);
              return new IdempotentInvoker(
                  new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));
            });
    assertThat(asked).isFalse();

    registrar.afterSingletonsInstantiated();

    assertThat(asked).isTrue();
  }
}
