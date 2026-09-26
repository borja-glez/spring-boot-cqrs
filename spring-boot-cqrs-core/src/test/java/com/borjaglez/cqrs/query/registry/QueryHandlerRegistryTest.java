package com.borjaglez.cqrs.query.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.CheckedThrowingQueryHandler;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.fixtures.TestQueryHandler;
import com.borjaglez.cqrs.fixtures.ThrowingQueryHandler;
import com.borjaglez.cqrs.query.QueryAlreadyRegisteredException;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;

class QueryHandlerRegistryTest {

  private QueryHandlerRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new QueryHandlerRegistry();
  }

  @Test
  void handlersAreRemoteUnlessRegisteredAsLocal() throws Exception {
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, new TestQueryHandler(), method, "test.query");
    QueryHandlerRegistry localRegistry = new QueryHandlerRegistry();
    localRegistry.register(TestQuery.class, new TestQueryHandler(), method, "test.query", false);

    assertThat(registry.getHandlerInfo(TestQuery.class).orElseThrow().remote()).isTrue();
    assertThat(localRegistry.getHandlerInfo(TestQuery.class).orElseThrow().remote()).isFalse();
  }

  @Test
  void handlerInfoWithoutTheRemoteFlagIsRemote() {
    assertThat(new QueryHandlerRegistry.HandlerInfo(new Object(), null, "test.query").remote())
        .isTrue();
  }

  @Test
  void registerAndHandleReturnsResult() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    TestQuery query = new TestQuery("world");
    Object result = registry.handle(query);

    assertThat(result).isEqualTo("result:world");
  }

  @Test
  void duplicateRegistrationThrows() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThatThrownBy(() -> registry.register(TestQuery.class, handler, method, "test.query"))
        .isInstanceOf(QueryAlreadyRegisteredException.class)
        .hasMessageContaining(TestQuery.class.getName());
  }

  @Test
  void unregisteredQueryThrows() {
    TestQuery query = new TestQuery("data");
    assertThatThrownBy(() -> registry.handle(query))
        .isInstanceOf(QueryNotRegisteredException.class)
        .hasMessageContaining(TestQuery.class.getName());
  }

  @Test
  void unregisteredQuerySubclassMentionsSuperclassHandler() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThatThrownBy(() -> registry.handle(new SubTestQuery("sub")))
        .isInstanceOf(QueryNotRegisteredException.class)
        .hasMessageContaining(SubTestQuery.class.getName())
        .hasMessageContaining("superclass " + TestQuery.class.getName())
        .hasMessageContaining("exact message class");
  }

  @Test
  void unregisteredQueryWithoutSuperclassHandlerKeepsPlainMessage() {
    assertThatThrownBy(() -> registry.handle(new TestQuery("data")))
        .isInstanceOf(QueryNotRegisteredException.class)
        .hasMessage("No handler registered for query: " + TestQuery.class.getName());
  }

  @Test
  void handleRethrowsRuntimeException() throws Exception {
    ThrowingQueryHandler handler = new ThrowingQueryHandler();
    Method method = ThrowingQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThatThrownBy(() -> registry.handle(new TestQuery("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("query handler error");
  }

  @Test
  void getRegisteredQueriesReturnsSet() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThat(registry.getRegisteredQueries()).containsExactly(TestQuery.class);
  }

  @Test
  void getHandlerInfoReturnsPresent() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    var info = registry.getHandlerInfo(TestQuery.class);

    assertThat(info).isPresent();
    assertThat(info.get().messageName()).isEqualTo("test.query");
    assertThat(info.get().bean()).isSameAs(handler);
  }

  @Test
  void getHandlerInfoReturnsEmptyForUnregistered() {
    assertThat(registry.getHandlerInfo(TestQuery.class)).isEmpty();
  }

  @Test
  void handleWrapsCheckedExceptionInQueryHandlerExecutionException() throws Exception {
    CheckedThrowingQueryHandler handler = new CheckedThrowingQueryHandler();
    Method method = CheckedThrowingQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThatThrownBy(() -> registry.handle(new TestQuery("data")))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  static class SubTestQuery extends TestQuery {
    SubTestQuery(String data) {
      super(data);
    }
  }
}
