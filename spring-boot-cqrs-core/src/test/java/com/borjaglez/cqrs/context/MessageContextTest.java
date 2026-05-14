package com.borjaglez.cqrs.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessageContextTest {

  @AfterEach
  void cleanUp() {
    MessageContext.clear();
  }

  @Test
  void emptyIsImmutableSingleton() {
    assertThat(MessageContext.empty().isEmpty()).isTrue();
    assertThat(MessageContext.empty().asMap()).isEmpty();
    assertThat(MessageContext.empty().correlationId()).isNull();
    assertThat(MessageContext.empty().get("anything")).isEmpty();
  }

  @Test
  void ofNullOrEmptyReturnsEmpty() {
    assertThat(MessageContext.of(null)).isSameAs(MessageContext.empty());
    assertThat(MessageContext.of(new HashMap<>())).isSameAs(MessageContext.empty());

    Map<String, String> nulls = new HashMap<>();
    nulls.put(null, "v");
    nulls.put("k", null);
    assertThat(MessageContext.of(nulls)).isSameAs(MessageContext.empty());
  }

  @Test
  void ofCopiesEntriesAndIsImmutable() {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("correlationId", "abc");
    entries.put("tenantId", "acme");

    MessageContext ctx = MessageContext.of(entries);
    entries.put("mutation", "bad");

    assertThat(ctx.asMap()).containsOnlyKeys("correlationId", "tenantId");
    assertThat(ctx.correlationId()).isEqualTo("abc");
    assertThat(ctx.get("tenantId")).contains("acme");
    assertThatThrownBy(() -> ctx.asMap().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void withReturnsNewInstance() {
    MessageContext ctx = MessageContext.empty().with("a", "1");
    MessageContext next = ctx.with("b", "2");

    assertThat(ctx.asMap()).containsOnlyKeys("a");
    assertThat(next.asMap()).containsOnlyKeys("a", "b");
    assertThat(ctx).isNotSameAs(next);
  }

  @Test
  void withRejectsNulls() {
    assertThatThrownBy(() -> MessageContext.empty().with(null, "v"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MessageContext.empty().with("k", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void mergeHandlesNullEmptyAndPrecedence() {
    MessageContext a = MessageContext.empty().with("k1", "a").with("shared", "a");
    MessageContext b = MessageContext.empty().with("k2", "b").with("shared", "b");

    assertThat(a.merge(null)).isSameAs(a);
    assertThat(a.merge(MessageContext.empty())).isSameAs(a);
    assertThat(MessageContext.empty().merge(b)).isSameAs(b);

    MessageContext merged = a.merge(b);
    assertThat(merged.asMap())
        .containsEntry("k1", "a")
        .containsEntry("k2", "b")
        .containsEntry("shared", "b");
  }

  @Test
  void scopeSetsAndRestoresPrevious() {
    assertThat(MessageContext.current().isEmpty()).isTrue();

    MessageContext outer = MessageContext.empty().with("x", "1");
    try (MessageContext.Scope s1 = MessageContext.scope(outer)) {
      assertThat(MessageContext.current()).isEqualTo(outer);

      MessageContext inner = outer.with("y", "2");
      try (MessageContext.Scope s2 = MessageContext.scope(inner)) {
        assertThat(MessageContext.current()).isEqualTo(inner);
      }

      assertThat(MessageContext.current()).isEqualTo(outer);
    }

    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void scopeCloseIsIdempotent() {
    MessageContext ctx = MessageContext.empty().with("k", "v");
    MessageContext.Scope scope = MessageContext.scope(ctx);
    scope.close();
    scope.close();
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void scopeAcceptsNull() {
    MessageContext.Scope scope = MessageContext.scope(null);
    assertThat(MessageContext.current().isEmpty()).isTrue();
    scope.close();
  }

  @Test
  void equalsAndHashCodeAndToString() {
    MessageContext a = MessageContext.empty().with("k", "v");
    MessageContext b = MessageContext.empty().with("k", "v");
    MessageContext c = MessageContext.empty().with("k", "w");

    assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(c).isNotEqualTo("str").isNotEqualTo(null);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("k=v");
  }
}
