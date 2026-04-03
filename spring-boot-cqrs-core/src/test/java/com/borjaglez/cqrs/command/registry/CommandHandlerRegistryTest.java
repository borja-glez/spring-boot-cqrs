package com.borjaglez.cqrs.command.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandAlreadyRegisteredException;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.CommandNotRegisteredException;
import com.borjaglez.cqrs.fixtures.CheckedThrowingCommandHandler;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestReturningCommandHandler;
import com.borjaglez.cqrs.fixtures.ThrowingCommandHandler;
import com.borjaglez.cqrs.fixtures.UnannotatedCommand;

class CommandHandlerRegistryTest {

  private CommandHandlerRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new CommandHandlerRegistry();
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
}
