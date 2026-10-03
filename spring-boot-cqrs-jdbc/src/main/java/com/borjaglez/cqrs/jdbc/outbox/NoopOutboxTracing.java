package com.borjaglez.cqrs.jdbc.outbox;

import java.util.Map;

final class NoopOutboxTracing implements OutboxTracing {

  static final NoopOutboxTracing INSTANCE = new NoopOutboxTracing();

  private NoopOutboxTracing() {}

  @Override
  public Map<String, String> capture() {
    return Map.of();
  }

  @Override
  public void run(Map<String, String> headers, Runnable action) {
    action.run();
  }
}
