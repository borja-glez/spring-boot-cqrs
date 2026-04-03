package com.borjaglez.cqrs.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultMiddlewareChainTest {

  @Test
  void emptyMiddlewareListGoesToTerminal() throws Exception {
    DefaultMiddlewareChain chain = new DefaultMiddlewareChain(List.of(), msg -> "terminal:" + msg);

    Object result = chain.proceed("hello");
    assertThat(result).isEqualTo("terminal:hello");
  }

  @Test
  void singleMiddlewareIsCalled() throws Exception {
    List<String> callOrder = new ArrayList<>();
    BusMiddleware middleware =
        (msg, next) -> {
          callOrder.add("middleware");
          return next.proceed(msg);
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(middleware),
            msg -> {
              callOrder.add("terminal");
              return "done";
            });

    Object result = chain.proceed("msg");
    assertThat(result).isEqualTo("done");
    assertThat(callOrder).containsExactly("middleware", "terminal");
  }

  @Test
  void multipleMiddlewaresCalledInOrder() throws Exception {
    List<String> callOrder = new ArrayList<>();
    BusMiddleware first =
        (msg, next) -> {
          callOrder.add("first");
          return next.proceed(msg);
        };
    BusMiddleware second =
        (msg, next) -> {
          callOrder.add("second");
          return next.proceed(msg);
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(first, second),
            msg -> {
              callOrder.add("terminal");
              return null;
            });

    chain.proceed("msg");
    assertThat(callOrder).containsExactly("first", "second", "terminal");
  }

  @Test
  void middlewareCanShortCircuit() throws Exception {
    List<String> callOrder = new ArrayList<>();
    BusMiddleware shortCircuit =
        (msg, next) -> {
          callOrder.add("short-circuit");
          return "blocked";
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(shortCircuit),
            msg -> {
              callOrder.add("terminal");
              return null;
            });

    Object result = chain.proceed("msg");
    assertThat(result).isEqualTo("blocked");
    assertThat(callOrder).containsExactly("short-circuit");
  }

  @Test
  void terminalHandlerIsCalledLast() throws Exception {
    List<String> callOrder = new ArrayList<>();
    BusMiddleware middleware =
        (msg, next) -> {
          callOrder.add("middleware");
          return next.proceed(msg);
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(middleware),
            msg -> {
              callOrder.add("terminal");
              return "terminal-result";
            });

    Object result = chain.proceed("msg");
    assertThat(result).isEqualTo("terminal-result");
    assertThat(callOrder).last().isEqualTo("terminal");
  }
}
