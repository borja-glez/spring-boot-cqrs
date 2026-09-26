package com.borjaglez.cqrs.query;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Getter
public abstract class Query {

  // Not final so deserializers that only populate non-final fields (Jackson 3 by default) keep the
  // identity the message was sent with. There are no setters: the value never changes after
  // construction or deserialization.
  private String queryId;

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
