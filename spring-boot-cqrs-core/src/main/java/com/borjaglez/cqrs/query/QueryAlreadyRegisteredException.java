package com.borjaglez.cqrs.query;

public class QueryAlreadyRegisteredException extends RuntimeException {

  public QueryAlreadyRegisteredException(Class<?> queryClass) {
    super("A handler is already registered for query: " + queryClass.getName());
  }
}
