package com.borjaglez.cqrs.query;

public class QueryHandlerExecutionException extends RuntimeException {

  public QueryHandlerExecutionException(Throwable cause) {
    super(cause);
  }
}
