package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.test.assertion.CommandBusAssert;
import com.borjaglez.cqrs.test.fixtures.TestCommand;

class SpyCommandBusTest {

  @Test
  void dispatchRecordsAndDelegates() {
    CommandBus delegate = mock(CommandBus.class);
    SpyCommandBus spy = new SpyCommandBus(delegate);
    TestCommand command = new TestCommand("a");

    spy.dispatch(command);

    verify(delegate).dispatch(command);
    assertThat(spy.recorded()).containsExactly(command);
  }

  @Test
  void dispatchAndWaitRecordsAndDelegates() {
    CommandBus delegate = mock(CommandBus.class);
    SpyCommandBus spy = new SpyCommandBus(delegate);
    TestCommand command = new TestCommand("a");

    spy.dispatchAndWait(command);

    verify(delegate).dispatchAndWait(command);
    assertThat(spy.recorded()).containsExactly(command);
  }

  @Test
  void dispatchAndReceiveReturnsDelegateResult() {
    CommandBus delegate = mock(CommandBus.class);
    SpyCommandBus spy = new SpyCommandBus(delegate);
    TestCommand command = new TestCommand("a");
    when(delegate.<String>dispatchAndReceive(command)).thenReturn("ok");

    String result = spy.dispatchAndReceive(command);

    assertThat(result).isEqualTo("ok");
    assertThat(spy.recorded()).containsExactly(command);
  }

  @Test
  void dispatchAndReceiveWithTypeReferenceDelegates() {
    CommandBus delegate = mock(CommandBus.class);
    SpyCommandBus spy = new SpyCommandBus(delegate);
    TestCommand command = new TestCommand("a");
    ParameterizedTypeReference<String> ref = new ParameterizedTypeReference<String>() {};
    when(delegate.<String>dispatchAndReceive(command, ref)).thenReturn("v");

    String result = spy.dispatchAndReceive(command, ref);

    assertThat(result).isEqualTo("v");
    assertThat(spy.recorded()).containsExactly(command);
  }

  @Test
  void recordingHappensEvenWhenDelegateThrows() {
    CommandBus delegate = mock(CommandBus.class);
    SpyCommandBus spy = new SpyCommandBus(delegate);
    TestCommand command = new TestCommand("a");
    doThrow(new IllegalStateException("boom")).when(delegate).dispatch(any(Command.class));

    assertThatThrownBy(() -> spy.dispatch(command)).isInstanceOf(IllegalStateException.class);
    assertThat(spy.recorded()).containsExactly(command);
  }

  @Test
  void clearEmptiesRecording() {
    SpyCommandBus spy = new SpyCommandBus(mock(CommandBus.class));
    spy.dispatch(new TestCommand("a"));

    spy.clear();

    assertThat(spy.recorded()).isEmpty();
  }

  @Test
  void assertProviderReturnsAssert() {
    SpyCommandBus spy = new SpyCommandBus(mock(CommandBus.class));

    CommandBusAssert provided = spy.assertThat();

    assertThat(provided).isNotNull();
  }
}
