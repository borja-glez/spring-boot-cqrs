package com.borjaglez.cqrs.fixtures;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;

/** Records published events and the correlation id seen while publishing; can fail on demand. */
public class TestRecordingEventBus implements EventBus {

  private final List<Event> published = new CopyOnWriteArrayList<>();
  private final List<String> correlationIds = new CopyOnWriteArrayList<>();
  private final Deque<RuntimeException> failures = new ConcurrentLinkedDeque<>();

  /** The next publish throws {@code failure}; queued failures apply one per call. */
  public void failNext(RuntimeException failure) {
    failures.add(failure);
  }

  @Override
  public void publish(Event event) {
    RuntimeException failure = failures.poll();
    if (failure != null) {
      throw failure;
    }
    correlationIds.add(MessageContext.current().correlationId());
    published.add(event);
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }

  public List<Event> published() {
    return published;
  }

  public List<String> correlationIds() {
    return correlationIds;
  }
}
