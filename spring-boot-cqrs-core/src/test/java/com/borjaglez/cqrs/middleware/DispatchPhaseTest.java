package com.borjaglez.cqrs.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DispatchPhaseTest {

  private static final BusMiddleware DEFAULT = (message, chain) -> chain.proceed(message);

  private static final BusMiddleware OUTBOUND_ONLY = new PhasedMiddleware(DispatchPhase.OUTBOUND);

  private static final BusMiddleware EVERYWHERE =
      new PhasedMiddleware(DispatchPhase.LOCAL, DispatchPhase.OUTBOUND, DispatchPhase.INBOUND);

  @Test
  void busMiddlewareRunsLocallyAndOnTheReceiverByDefault() {
    assertThat(DEFAULT.phases())
        .containsExactlyInAnyOrder(DispatchPhase.LOCAL, DispatchPhase.INBOUND);
  }

  @Test
  void selectKeepsOnlyTheMiddlewaresDeclaringThePhaseInOrder() {
    List<BusMiddleware> middlewares = List.of(EVERYWHERE, DEFAULT, OUTBOUND_ONLY);

    assertThat(DispatchPhase.LOCAL.select(middlewares)).containsExactly(EVERYWHERE, DEFAULT);
    assertThat(DispatchPhase.INBOUND.select(middlewares)).containsExactly(EVERYWHERE, DEFAULT);
    assertThat(DispatchPhase.OUTBOUND.select(middlewares))
        .containsExactly(EVERYWHERE, OUTBOUND_ONLY);
  }

  @Test
  void selectReturnsAnUnmodifiableList() {
    List<BusMiddleware> selected = DispatchPhase.LOCAL.select(List.of(DEFAULT));

    assertThatThrownBy(() -> selected.add(DEFAULT))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void selectOfNullIsEmpty() {
    assertThat(DispatchPhase.OUTBOUND.select(null)).isEmpty();
  }

  @Test
  void selectRejectsMiddlewareWithNullPhases() {
    BusMiddleware broken = new PhasedMiddleware((DispatchPhase[]) null);

    assertThatThrownBy(() -> DispatchPhase.LOCAL.select(List.of(broken)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining(PhasedMiddleware.class.getName())
        .hasMessageContaining("phases()");
  }

  private static final class PhasedMiddleware implements BusMiddleware {

    private final Set<DispatchPhase> phases;

    PhasedMiddleware(DispatchPhase... phases) {
      this.phases = phases == null ? null : Set.of(phases);
    }

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }

    @Override
    public Set<DispatchPhase> phases() {
      return phases;
    }
  }
}
