package com.borjaglez.cqrs.query;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Getter
public abstract class Query {

  private final String queryId;

  protected Query() {
    this.queryId = UUID.randomUUID().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Query query)) return false;
    return Objects.equals(queryId, query.queryId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(queryId);
  }
}
