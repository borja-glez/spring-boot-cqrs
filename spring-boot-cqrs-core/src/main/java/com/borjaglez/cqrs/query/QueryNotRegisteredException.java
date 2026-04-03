package com.borjaglez.cqrs.query;

public class QueryNotRegisteredException extends RuntimeException {

  public QueryNotRegisteredException(Class<?> queryClass) {
    super("No handler registered for query: " + queryClass.getName());
  }
}
