package com.borjaglez.cqrs.query;

import org.springframework.core.ParameterizedTypeReference;

public interface QueryBus {

  <R> R ask(Query query);

  default <R> R ask(Query query, ParameterizedTypeReference<R> responseType) {
    return ask(query);
  }
}
