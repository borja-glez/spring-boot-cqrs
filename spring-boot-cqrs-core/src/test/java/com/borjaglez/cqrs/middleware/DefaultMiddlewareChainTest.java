package com.borjaglez.cqrs.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

  @Test
  void middlewareCallingProceedTwiceRerunsLaterMiddlewaresAndTerminal() throws Exception {
    AtomicInteger laterCalls = new AtomicInteger();
    AtomicInteger terminalCalls = new AtomicInteger();
    BusMiddleware retryOnce =
        (msg, next) -> {
          try {
            return next.proceed(msg);
          } catch (IllegalStateException first) {
            return next.proceed(msg);
          }
        };
    BusMiddleware counting =
        (msg, next) -> {
          laterCalls.incrementAndGet();
          return next.proceed(msg);
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(retryOnce, counting),
            msg -> {
              if (terminalCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
              }
              return "ok:" + msg;
            });

    Object result = chain.proceed("msg");
    assertThat(result).isEqualTo("ok:msg");
    assertThat(laterCalls).hasValue(2);
    assertThat(terminalCalls).hasValue(2);
  }

  @Test
  void middlewareCallingProceedTwiceRunsFullRestOfChainInOrderEachTime() throws Exception {
    List<String> callOrder = new ArrayList<>();
    BusMiddleware twice =
        (msg, next) -> {
          callOrder.add("twice");
          next.proceed(msg);
          return next.proceed(msg);
        };
    BusMiddleware second =
        (msg, next) -> {
          callOrder.add("second");
          return next.proceed(msg);
        };
    BusMiddleware third =
        (msg, next) -> {
          callOrder.add("third");
          return next.proceed(msg);
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(twice, second, third),
            msg -> {
              callOrder.add("terminal");
              return null;
            });

    chain.proceed("msg");
    assertThat(callOrder)
        .containsExactly("twice", "second", "third", "terminal", "second", "third", "terminal");
  }

  @Test
  void exceptionFromLaterMiddlewareReachesEarlierMiddleware() throws Exception {
    List<String> callOrder = new ArrayList<>();
    IllegalArgumentException failure = new IllegalArgumentException("boom");
    BusMiddleware first =
        (msg, next) -> {
          try {
            return next.proceed(msg);
          } catch (IllegalArgumentException e) {
            callOrder.add("first caught " + e.getMessage());
            throw e;
          }
        };
    BusMiddleware failing =
        (msg, next) -> {
          throw failure;
        };

    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            List.of(first, failing),
            msg -> {
              callOrder.add("terminal");
              return null;
            });

    assertThatThrownBy(() -> chain.proceed("msg")).isSameAs(failure);
    assertThat(callOrder).containsExactly("first caught boom");
  }
}
