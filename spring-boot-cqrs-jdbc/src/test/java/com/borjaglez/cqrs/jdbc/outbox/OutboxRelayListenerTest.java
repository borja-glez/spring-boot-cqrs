package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayListenerTest {

  private static final class Recording implements OutboxRelayListener {
    private final List<String> calls = new ArrayList<>();

    @Override
    public void published(int count) {
      calls.add("published:" + count);
    }

    @Override
    public void publishFailed() {
      calls.add("publishFailed");
    }

    @Override
    public void setAside(int count) {
      calls.add("setAside:" + count);
    }
  }

  private static final class Throwing implements OutboxRelayListener {
    @Override
    public void published(int count) {
      throw new IllegalStateException("boom published");
    }

    @Override
    public void publishFailed() {
      throw new IllegalStateException("boom failed");
    }

    @Override
    public void setAside(int count) {
      throw new IllegalStateException("boom aside");
    }
  }

  @Test
  void compositeOfNothingIsTheNoop() {
    OutboxRelayListener none = OutboxRelayListener.composite(List.of());

    none.published(1);
    none.publishFailed();
    none.setAside(1);
  }

  @Test
  void aSingleThrowingListenerIsIsolatedToo() {
    OutboxRelayListener single = OutboxRelayListener.composite(List.of(new Throwing()));

    single.published(1);
  }

  @Test
  void compositeCallsEveryListener() {
    Recording first = new Recording();
    Recording second = new Recording();
    OutboxRelayListener composite = OutboxRelayListener.composite(List.of(first, second));

    composite.published(2);
    composite.publishFailed();
    composite.setAside(1);

    assertThat(first.calls).containsExactly("published:2", "publishFailed", "setAside:1");
    assertThat(second.calls).isEqualTo(first.calls);
  }

  @Test
  void aThrowingListenerIsLoggedAndTheOthersStillRun(CapturedOutput output) {
    Recording after = new Recording();
    OutboxRelayListener composite = OutboxRelayListener.composite(List.of(new Throwing(), after));

    composite.published(2);
    composite.publishFailed();
    composite.setAside(1);

    assertThat(after.calls).containsExactly("published:2", "publishFailed", "setAside:1");
    assertThat(output.getAll())
        .contains("boom published", "boom failed", "boom aside")
        .contains("OutboxRelayListener");
  }
}
