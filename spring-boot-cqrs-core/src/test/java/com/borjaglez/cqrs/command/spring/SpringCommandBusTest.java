package com.borjaglez.cqrs.command.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.middleware.BusMiddleware;

@ExtendWith(MockitoExtension.class)
class SpringCommandBusTest {

  @Test
  void dispatchCallsRegistry() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    SpringCommandBus bus = new SpringCommandBus(registry, List.of());

    TestCommand command = new TestCommand("data");
    bus.dispatch(command);

    verify(registry).handle(command);
  }

  @Test
  void dispatchAndWaitCallsRegistry() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    SpringCommandBus bus = new SpringCommandBus(registry, List.of());

    TestCommand command = new TestCommand("data");
    bus.dispatchAndWait(command);

    verify(registry).handle(command);
  }

  @Test
  void dispatchAndWaitRuntimeExceptionPropagated() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    when(registry.handle(any())).thenThrow(new IllegalArgumentException("bad"));

    SpringCommandBus bus = new SpringCommandBus(registry, List.of());

    assertThatThrownBy(() -> bus.dispatchAndWait(new TestCommand("data")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad");
  }

  @Test
  void dispatchAndWaitCheckedExceptionWrapped() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);

    BusMiddleware throwingMiddleware =
        (msg, chain) -> {
          throw new Exception("checked");
        };

    SpringCommandBus bus = new SpringCommandBus(registry, List.of(throwingMiddleware));

    assertThatThrownBy(() -> bus.dispatchAndWait(new TestCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void dispatchAndReceiveReturnsResult() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    TestCommand command = new TestCommand("data");
    when(registry.handle(command)).thenReturn("result");

    SpringCommandBus bus = new SpringCommandBus(registry, List.of());
    String result = bus.dispatchAndReceive(command);

    assertThat(result).isEqualTo("result");
  }

  @Test
  void middlewareChainIsInvoked() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    List<String> callOrder = new ArrayList<>();

    BusMiddleware middleware =
        (msg, chain) -> {
          callOrder.add("middleware");
          return chain.proceed(msg);
        };

    SpringCommandBus bus = new SpringCommandBus(registry, List.of(middleware));
    bus.dispatch(new TestCommand("data"));

    assertThat(callOrder).containsExactly("middleware");
    verify(registry).handle(any());
  }

  @Test
  void runtimeExceptionPropagated() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    when(registry.handle(any())).thenThrow(new IllegalArgumentException("bad"));

    SpringCommandBus bus = new SpringCommandBus(registry, List.of());

    assertThatThrownBy(() -> bus.dispatch(new TestCommand("data")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad");
  }

  @Test
  void checkedExceptionWrappedInCommandHandlerExecutionException() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);

    BusMiddleware throwingMiddleware =
        (msg, chain) -> {
          throw new Exception("checked");
        };

    SpringCommandBus bus = new SpringCommandBus(registry, List.of(throwingMiddleware));

    assertThatThrownBy(() -> bus.dispatch(new TestCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void dispatchAndReceiveCheckedExceptionWrapped() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);

    BusMiddleware throwingMiddleware =
        (msg, chain) -> {
          throw new Exception("checked");
        };

    SpringCommandBus bus = new SpringCommandBus(registry, List.of(throwingMiddleware));

    assertThatThrownBy(() -> bus.dispatchAndReceive(new TestCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void dispatchAndReceiveRuntimeExceptionPropagated() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    when(registry.handle(any())).thenThrow(new IllegalStateException("runtime"));

    SpringCommandBus bus = new SpringCommandBus(registry, List.of());

    assertThatThrownBy(() -> bus.dispatchAndReceive(new TestCommand("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("runtime");
  }
}
