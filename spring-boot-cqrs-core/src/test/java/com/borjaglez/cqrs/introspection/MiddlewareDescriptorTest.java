package com.borjaglez.cqrs.introspection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

class MiddlewareDescriptorTest {

  @Test
  void accessors() {
    MiddlewareDescriptor descriptor = new MiddlewareDescriptor(TestMiddleware.class, 100, true);

    assertThat(descriptor.middlewareType()).isEqualTo(TestMiddleware.class);
    assertThat(descriptor.order()).isEqualTo(100);
    assertThat(descriptor.isObservability()).isTrue();
  }

  @Test
  void equalityAndHashCode() {
    MiddlewareDescriptor d1 = new MiddlewareDescriptor(TestMiddleware.class, 100, true);
    MiddlewareDescriptor d2 = new MiddlewareDescriptor(TestMiddleware.class, 100, true);
    MiddlewareDescriptor d3 = new MiddlewareDescriptor(TestMiddleware.class, 200, false);

    assertThat(d1).isEqualTo(d2);
    assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    assertThat(d1).isNotEqualTo(d3);
  }

  @Test
  void toStringContainsFields() {
    MiddlewareDescriptor descriptor = new MiddlewareDescriptor(TestMiddleware.class, 100, false);

    String str = descriptor.toString();
    assertThat(str).contains("100");
    assertThat(str).contains("TestMiddleware");
  }

  static class TestMiddleware implements BusMiddleware {
    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }
  }
}
