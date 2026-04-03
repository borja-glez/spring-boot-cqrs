package com.borjaglez.cqrs.query;

public interface QueryBus {

  <R> R ask(Query query);
}
