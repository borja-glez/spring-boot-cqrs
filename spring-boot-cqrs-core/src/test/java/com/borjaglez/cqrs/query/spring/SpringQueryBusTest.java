package com.borjaglez.cqrs.query.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

@ExtendWith(MockitoExtension.class)
class SpringQueryBusTest {

  @Test
  void askReturnsResult() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);
    TestQuery query = new TestQuery("data");
    when(registry.handle(query)).thenReturn("answer");

    SpringQueryBus bus = new SpringQueryBus(registry, List.of());
    String result = bus.ask(query);

    assertThat(result).isEqualTo("answer");
  }

  @Test
  void middlewareChainIsInvoked() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);
    when(registry.handle(any())).thenReturn("answer");

    List<String> callOrder = new ArrayList<>();
    BusMiddleware middleware =
        (msg, chain) -> {
          callOrder.add("middleware");
          return chain.proceed(msg);
        };

    SpringQueryBus bus = new SpringQueryBus(registry, List.of(middleware));
    bus.ask(new TestQuery("data"));

    assertThat(callOrder).containsExactly("middleware");
  }

  @Test
  void runtimeExceptionPropagated() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);
    when(registry.handle(any())).thenThrow(new IllegalArgumentException("bad"));

    SpringQueryBus bus = new SpringQueryBus(registry, List.of());

    assertThatThrownBy(() -> bus.ask(new TestQuery("data")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkedExceptionWrapped() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);

    BusMiddleware throwingMiddleware =
        (msg, chain) -> {
          throw new Exception("checked");
        };

    SpringQueryBus bus = new SpringQueryBus(registry, List.of(throwingMiddleware));

    assertThatThrownBy(() -> bus.ask(new TestQuery("data")))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void askWithTypeReferenceDelegatesToUntyped() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);
    TestQuery query = new TestQuery("data");
    when(registry.handle(query)).thenReturn("answer");

    SpringQueryBus bus = new SpringQueryBus(registry, List.of());
    String result = bus.ask(query, new ParameterizedTypeReference<String>() {});

    assertThat(result).isEqualTo("answer");
  }
}
