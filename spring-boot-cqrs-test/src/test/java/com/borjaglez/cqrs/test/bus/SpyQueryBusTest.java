package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.test.assertion.QueryBusAssert;
import com.borjaglez.cqrs.test.fixtures.TestQuery;

class SpyQueryBusTest {

  @Test
  void askRecordsAndDelegates() {
    QueryBus delegate = mock(QueryBus.class);
    SpyQueryBus spy = new SpyQueryBus(delegate);
    TestQuery query = new TestQuery("a");
    when(delegate.<String>ask(query)).thenReturn("answer");

    String result = spy.ask(query);

    assertThat(result).isEqualTo("answer");
    assertThat(spy.recorded()).containsExactly(query);
  }

  @Test
  void askWithTypeReferenceRecordsAndDelegates() {
    QueryBus delegate = mock(QueryBus.class);
    SpyQueryBus spy = new SpyQueryBus(delegate);
    TestQuery query = new TestQuery("a");
    ParameterizedTypeReference<String> ref = new ParameterizedTypeReference<String>() {};
    when(delegate.<String>ask(query, ref)).thenReturn("typed");

    String result = spy.ask(query, ref);

    assertThat(result).isEqualTo("typed");
    assertThat(spy.recorded()).containsExactly(query);
  }

  @Test
  void clearEmptiesRecording() {
    SpyQueryBus spy = new SpyQueryBus(mock(QueryBus.class));
    spy.ask(new TestQuery("a"));

    spy.clear();

    assertThat(spy.recorded()).isEmpty();
  }

  @Test
  void assertProviderReturnsAssert() {
    SpyQueryBus spy = new SpyQueryBus(mock(QueryBus.class));

    QueryBusAssert provided = spy.assertThat();

    assertThat(provided).isNotNull();
  }
}
