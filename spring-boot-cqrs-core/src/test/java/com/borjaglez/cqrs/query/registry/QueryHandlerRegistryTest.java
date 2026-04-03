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
  void handleWrapsCheckedExceptionInQueryHandlerExecutionException() throws Exception {
    CheckedThrowingQueryHandler handler = new CheckedThrowingQueryHandler();
    Method method = CheckedThrowingQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, handler, method, "test.query");

    assertThatThrownBy(() -> registry.handle(new TestQuery("data")))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }
}
