package com.borjaglez.cqrs.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestQuery;

class QueryTest {

  @Test
  void queryIdIsGenerated() {
    TestQuery query = new TestQuery("data");
    assertThat(query.getQueryId()).isNotNull().isNotEmpty();
  }

  @Test
  void twoQueriesHaveDifferentIds() {
    TestQuery query1 = new TestQuery("data");
    TestQuery query2 = new TestQuery("data");
    assertThat(query1.getQueryId()).isNotEqualTo(query2.getQueryId());
  }

  @Test
  void equalsBasedOnQueryId() {
    TestQuery query = new TestQuery("data");
    assertThat(query).isEqualTo(query);
  }

  @Test
  void notEqualToDifferentQuery() {
    TestQuery query1 = new TestQuery("data");
    TestQuery query2 = new TestQuery("data");
    assertThat(query1).isNotEqualTo(query2);
  }

  @Test
  void notEqualToNull() {
    TestQuery query = new TestQuery("data");
    assertThat(query).isNotEqualTo(null);
  }

  @Test
  void notEqualToDifferentType() {
    TestQuery query = new TestQuery("data");
    assertThat(query).isNotEqualTo("not a query");
  }

  @Test
  void hashCodeBasedOnQueryId() {
    TestQuery query = new TestQuery("data");
    assertThat(query.hashCode()).isEqualTo(query.hashCode());
  }

  @Test
  void dataIsStored() {
    TestQuery query = new TestQuery("hello");
    assertThat(query.getData()).isEqualTo("hello");
  }
}
