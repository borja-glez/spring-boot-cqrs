package com.borjaglez.cqrs.command.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandAlreadyRegisteredException;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.CommandNotRegisteredException;
import com.borjaglez.cqrs.fixtures.CheckedThrowingCommandHandler;
import com.borjaglez.cqrs.fixtures.CountingCommandHandlers;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestReturningCommandHandler;
import com.borjaglez.cqrs.fixtures.ThrowingCommandHandler;
import com.borjaglez.cqrs.fixtures.UnannotatedCommand;
import com.borjaglez.cqrs.idempotency.DuplicateMessageException;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

class CommandHandlerRegistryTest {

  private CommandHandlerRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new CommandHandlerRegistry();
  }

  @Test
  void handlersAreRemoteUnlessRegisteredAsLocal() throws Exception {
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, new TestCommandHandler(), method, "test.command", false);
    CommandHandlerRegistry localRegistry = new CommandHandlerRegistry();
    localRegistry.register(
        TestCommand.class, new TestCommandHandler(), method, "test.command", false, false);

    assertThat(registry.getHandlerInfo(TestCommand.class).orElseThrow().remote()).isTrue();
    assertThat(localRegistry.getHandlerInfo(TestCommand.class).orElseThrow().remote()).isFalse();
  }

  @Test
  void handlerInfoWithoutTheRemoteFlagIsRemote() {
    var info = new CommandHandlerRegistry.HandlerInfo(new Object(), null, "test.command", true);

    assertThat(info.remote()).isTrue();
    assertThat(info.requiresValidation()).isTrue();
  }

  @Test
  void registerAndHandle() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    TestCommand command = new TestCommand("hello");
    registry.handle(command);

    assertThat(handler.getLastHandledData()).isEqualTo("hello");
  }

  @Test
  void handleReturnsResult() throws Exception {
    TestReturningCommandHandler handler = new TestReturningCommandHandler();
    Method method = TestReturningCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    TestCommand command = new TestCommand("result-data");
    Object result = registry.handle(command);

    assertThat(result).isEqualTo("result-data");
  }

  @Test
  void duplicateRegistrationThrows() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    assertThatThrownBy(
            () -> registry.register(TestCommand.class, handler, method, "test.command", false))
        .isInstanceOf(CommandAlreadyRegisteredException.class)
        .hasMessageContaining(TestCommand.class.getName());
  }

  @Test
  void unregisteredCommandThrows() {
    TestCommand command = new TestCommand("data");
    assertThatThrownBy(() -> registry.handle(command))
        .isInstanceOf(CommandNotRegisteredException.class)
        .hasMessageContaining(TestCommand.class.getName());
  }

  @Test
  void unregisteredCommandSubclassMentionsSuperclassHandler() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    assertThatThrownBy(() -> registry.handle(new SubTestCommand("sub")))
        .isInstanceOf(CommandNotRegisteredException.class)
        .hasMessageContaining(SubTestCommand.class.getName())
        .hasMessageContaining("superclass " + TestCommand.class.getName())
        .hasMessageContaining("exact message class");
    assertThat(handler.getLastHandledData()).isNull();
  }

  @Test
  void unregisteredCommandWithoutSuperclassHandlerKeepsPlainMessage() {
    assertThatThrownBy(() -> registry.handle(new TestCommand("data")))
        .isInstanceOf(CommandNotRegisteredException.class)
        .hasMessage("No handler registered for command: " + TestCommand.class.getName());
  }

  @Test
  void handleRethrowsRuntimeException() throws Exception {
    ThrowingCommandHandler handler = new ThrowingCommandHandler();
    Method method = ThrowingCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    assertThatThrownBy(() -> registry.handle(new TestCommand("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("command handler error");
  }

  @Test
  void getRegisteredCommandsReturnsSet() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", false);

    assertThat(registry.getRegisteredCommands()).containsExactly(TestCommand.class);
  }

  @Test
  void getHandlerInfoReturnsPresent() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, handler, method, "test.command", true);

    var info = registry.getHandlerInfo(TestCommand.class);
    assertThat(info).isPresent();
    assertThat(info.get().bean()).isSameAs(handler);
    assertThat(info.get().messageName()).isEqualTo("test.command");
    assertThat(info.get().requiresValidation()).isTrue();
  }

  @Test
  void getHandlerInfoReturnsEmptyForUnregistered() {
    assertThat(registry.getHandlerInfo(UnannotatedCommand.class)).isEmpty();
  }

  @Test
  void handleWrapsCheckedExceptionInCommandHandlerExecutionException() throws Exception {
    CheckedThrowingCommandHandler handler = new CheckedThrowingCommandHandler();
    Method method =
        CheckedThrowingCommandHandler.class.getMethod("handle", UnannotatedCommand.class);
    registry.register(UnannotatedCommand.class, handler, method, "test.command", false);

    assertThatThrownBy(() -> registry.handle(new UnannotatedCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class)
        .hasRootCauseMessage("checked command error");
  }

  static class SubTestCommand extends TestCommand {
    SubTestCommand(String data) {
      super(data);
    }
  }

  @Test
  void findsTheHandledCommandByItsMessageName() throws Exception {
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(TestCommand.class, new TestCommandHandler(), method, "test.command", false);

    assertThat(registry.findMessageClass("test.command")).contains(TestCommand.class);
    assertThat(registry.findMessageClass("other.command")).isEmpty();
  }

  @Test
  void commandWithoutCqrsMessageIsNotFoundByItsMessageName() throws Exception {
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(
        UnannotatedCommand.class, new TestCommandHandler(), method, "unannotated-command", false);

    assertThat(registry.findMessageClass("unannotated-command")).isEmpty();
  }

  private IdempotentInvoker invoker() {
    return new IdempotentInvoker(
        new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));
  }

  @Test
  void duplicateVoidCommandIsSkipped() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThat(registry.handle(command)).isNull();
    assertThat(registry.handle(command)).isNull();

    assertThat(handlers.calls).hasValue(1);
  }

  @Test
  void duplicateCommandWithAResultThrows() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handleAndReturn", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handleAndReturn");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThat(registry.handle(command)).isEqualTo("result-1");
    assertThatThrownBy(() -> registry.handle(command))
        .isInstanceOf(DuplicateMessageException.class)
        .hasMessage(
            "Message "
                + command.getCommandId()
                + " was already processed by idempotent handler orders#handleAndReturn");
    assertThat(handlers.calls).hasValue(1);
  }

  @Test
  void failedIdempotentCommandRunsAgain() throws Exception {
    Method method = ThrowingCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(
        TestCommand.class,
        new ThrowingCommandHandler(),
        method,
        "test.command",
        false,
        true,
        "throwing#handle");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThatThrownBy(() -> registry.handle(command)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> registry.handle(command))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(DuplicateMessageException.class);
  }

  @Test
  void checkedFailureOfAnIdempotentCommandIsWrapped() throws Exception {
    Method method =
        CheckedThrowingCommandHandler.class.getMethod("handle", UnannotatedCommand.class);
    registry.register(
        UnannotatedCommand.class,
        new CheckedThrowingCommandHandler(),
        method,
        "test.command",
        false,
        true,
        "checked#handle");
    registry.setIdempotentInvoker(invoker());

    assertThatThrownBy(() -> registry.handle(new UnannotatedCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class);
  }

  @Test
  void idempotentCommandWithoutInvokerFailsLoudly() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");

    assertThatThrownBy(() -> registry.handle(new TestCommand("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Handler orders#handle is @Idempotent but no IdempotencyStore");
    assertThat(handlers.calls).hasValue(0);
  }

  @Test
  void handlerInfoWithThePreviousCanonicalSignatureIsNotIdempotent() {
    CommandHandlerRegistry.HandlerInfo info =
        new CommandHandlerRegistry.HandlerInfo(
            new Object(), MethodHandles.constant(String.class, "x"), "name", false, true);

    assertThat(info.idempotencyKey()).isNull();
    assertThat(info.idempotent()).isFalse();
  }
}
