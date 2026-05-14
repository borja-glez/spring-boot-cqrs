package com.borjaglez.cqrs.test.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.middleware.BusMiddleware;

class HandlersTest {

  @Test
  void invokeWithoutMiddlewareCallsHandler() throws Exception {
    String result = Handlers.invoke("hello", String::toUpperCase);

    assertThat(result).isEqualTo("HELLO");
  }

  @Test
  void invokeRunsMiddlewareChainInOrder() throws Exception {
    StringBuilder trace = new StringBuilder();
    BusMiddleware first =
        (msg, chain) -> {
          trace.append("[1>");
          Object r = chain.proceed(msg);
          trace.append("<1]");
          return r;
        };
    BusMiddleware second =
        (msg, chain) -> {
          trace.append("[2>");
          Object r = chain.proceed(msg);
          trace.append("<2]");
          return r;
        };

    String result = Handlers.invoke("x", String::toUpperCase, first, second);

    assertThat(result).isEqualTo("X");
    assertThat(trace.toString()).isEqualTo("[1>[2><2]<1]");
  }

  @Test
  void invokePropagatesHandlerException() {
    assertThatThrownBy(
            () ->
                Handlers.invoke(
                    "x",
                    msg -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void invokeAcceptsNullMiddlewareArray() throws Exception {
    String result = Handlers.invoke("hi", String::toUpperCase, (BusMiddleware[]) null);

    assertThat(result).isEqualTo("HI");
  }
}
