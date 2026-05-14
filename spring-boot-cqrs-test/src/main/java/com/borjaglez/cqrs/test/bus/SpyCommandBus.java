package com.borjaglez.cqrs.test.bus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.assertj.core.api.AssertProvider;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.test.assertion.CommandBusAssert;

public final class SpyCommandBus implements CommandBus, AssertProvider<CommandBusAssert> {

  private final CommandBus delegate;
  private final List<Command> recorded = new CopyOnWriteArrayList<>();

  public SpyCommandBus(CommandBus delegate) {
    this.delegate = delegate;
  }

  public List<Command> recorded() {
    return List.copyOf(recorded);
  }

  public void clear() {
    recorded.clear();
  }

  @Override
  public CommandBusAssert assertThat() {
    return new CommandBusAssert(this);
  }

  @Override
  public void dispatch(Command command) {
    recorded.add(command);
    delegate.dispatch(command);
  }

  @Override
  public void dispatchAndWait(Command command) {
    recorded.add(command);
    delegate.dispatchAndWait(command);
  }

  @Override
  public <R> R dispatchAndReceive(Command command) {
    recorded.add(command);
    return delegate.dispatchAndReceive(command);
  }

  @Override
  public <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType) {
    recorded.add(command);
    return delegate.dispatchAndReceive(command, responseType);
  }
}
