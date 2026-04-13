package com.borjaglez.cqrs.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.borjaglez.cqrs.middleware.MiddlewareChain;

class ContextPropagationMiddlewareTest {

  @BeforeEach
  @AfterEach
  void cleanUp() {
    MessageContext.clear();
    MDC.clear();
  }

  @Test
  void defaultConstructorGeneratesUuidCorrelationId() throws Exception {
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(true, List.of("correlationId"));

    java.util.concurrent.atomic.AtomicReference<String> observed =
        new java.util.concurrent.atomic.AtomicReference<>();
    middleware.process(
        "msg",
        msg -> {
          observed.set(MessageContext.current().correlationId());
          return null;
        });

    assertThat(observed.get()).isNotBlank();
    java.util.UUID.fromString(observed.get());
  }

  @Test
  void generatesCorrelationIdWhenMissing() throws Exception {
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(true, List.of("correlationId"), () -> "generated-id");

    AtomicReference<String> observed = new AtomicReference<>();
    AtomicReference<String> mdcValue = new AtomicReference<>();
    MiddlewareChain chain =
        msg -> {
          observed.set(MessageContext.current().correlationId());
          mdcValue.set(MDC.get("correlationId"));
          return "ok";
        };

    Object result = middleware.process("msg", chain);

    assertThat(result).isEqualTo("ok");
    assertThat(observed.get()).isEqualTo("generated-id");
    assertThat(mdcValue.get()).isEqualTo("generated-id");
    assertThat(MessageContext.current().isEmpty()).isTrue();
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void doesNotGenerateCorrelationIdWhenDisabled() throws Exception {
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(false, List.of("correlationId"));

    AtomicReference<String> observed = new AtomicReference<>();
    MiddlewareChain chain =
        msg -> {
          observed.set(MessageContext.current().correlationId());
          return null;
        };

    middleware.process("msg", chain);

    assertThat(observed.get()).isNull();
  }

  @Test
  void preservesIncomingContextAndMirrorsMultipleKeys() throws Exception {
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(true, Arrays.asList("correlationId", "tenantId"));

    MessageContext incoming =
        MessageContext.empty().with("correlationId", "cid-1").with("tenantId", "acme");

    AtomicReference<String> correlation = new AtomicReference<>();
    AtomicReference<String> tenant = new AtomicReference<>();
    MiddlewareChain chain =
        msg -> {
          correlation.set(MDC.get("correlationId"));
          tenant.set(MDC.get("tenantId"));
          return null;
        };

    try (MessageContext.Scope ignored = MessageContext.scope(incoming)) {
      middleware.process("msg", chain);
    }

    assertThat(correlation.get()).isEqualTo("cid-1");
    assertThat(tenant.get()).isEqualTo("acme");
    assertThat(MDC.get("correlationId")).isNull();
    assertThat(MDC.get("tenantId")).isNull();
  }

  @Test
  void restoresPreviousMdcOnExit() throws Exception {
    MDC.put("correlationId", "outer");
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(false, List.of("correlationId"));

    MessageContext ctx = MessageContext.empty().with("correlationId", "inner");
    MiddlewareChain chain =
        msg -> {
          assertThat(MDC.get("correlationId")).isEqualTo("inner");
          return null;
        };

    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      middleware.process("msg", chain);
    }

    assertThat(MDC.get("correlationId")).isEqualTo("outer");
  }

  @Test
  void restoresMdcOnException() {
    MDC.put("correlationId", "outer");
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(true, List.of("correlationId"), () -> "generated");

    MiddlewareChain chain =
        msg -> {
          throw new IllegalStateException("boom");
        };

    assertThatThrownBy(() -> middleware.process("msg", chain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get("correlationId")).isEqualTo("outer");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void emptyMdcKeysListJustPropagatesContext() throws Exception {
    ContextPropagationMiddleware middleware =
        new ContextPropagationMiddleware(true, null, () -> "cid");

    AtomicReference<MessageContext> observed = new AtomicReference<>();
    middleware.process(
        "msg",
        msg -> {
          observed.set(MessageContext.current());
          return null;
        });

    assertThat(observed.get().correlationId()).isEqualTo("cid");
  }

  @Test
  void constructorRejectsNullSupplier() {
    assertThatThrownBy(() -> new ContextPropagationMiddleware(true, List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void headerMapAndFromHeadersRoundTrip() {
    MessageContext ctx =
        MessageContext.empty().with("correlationId", "abc").with("tenantId", "acme");
    Map<String, String> headers = ContextPropagationMiddleware.headerMap(ctx, "cqrs.context.");

    assertThat(headers)
        .containsEntry("cqrs.context.correlationId", "abc")
        .containsEntry("cqrs.context.tenantId", "acme");

    MessageContext restored = ContextPropagationMiddleware.fromHeaders(headers, "cqrs.context.");
    assertThat(restored).isEqualTo(ctx);
  }

  @Test
  void headerMapHandlesEmptyAndNullPrefix() {
    assertThat(ContextPropagationMiddleware.headerMap(null, "cqrs.context."))
        .isEqualTo(Collections.emptyMap());
    assertThat(ContextPropagationMiddleware.headerMap(MessageContext.empty(), "cqrs.context."))
        .isEqualTo(Collections.emptyMap());

    MessageContext ctx = MessageContext.empty().with("k", "v");
    assertThat(ContextPropagationMiddleware.headerMap(ctx, null)).containsEntry("k", "v");
  }

  @Test
  void fromHeadersIgnoresForeignAndNullEntries() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("cqrs.context.correlationId", "abc");
    headers.put("cqrs.context.", "orphan");
    headers.put("other.header", "ignored");
    headers.put("cqrs.context.tenant", null);
    headers.put(null, "noKey");

    MessageContext ctx = ContextPropagationMiddleware.fromHeaders(headers, "cqrs.context.");
    assertThat(ctx.asMap()).containsOnlyKeys("correlationId");
    assertThat(ctx.correlationId()).isEqualTo("abc");

    assertThat(ContextPropagationMiddleware.fromHeaders(null, "cqrs.context."))
        .isSameAs(MessageContext.empty());
    assertThat(ContextPropagationMiddleware.fromHeaders(Collections.emptyMap(), "cqrs.context."))
        .isSameAs(MessageContext.empty());
    assertThat(ContextPropagationMiddleware.fromHeaders(Map.of("k", "v"), null).asMap())
        .containsEntry("k", "v");
  }
}
