package com.borjaglez.cqrs.query;

public class QueryNotRegisteredException extends RuntimeException {

  public QueryNotRegisteredException(Class<?> queryClass) {
    super("No handler registered for query: " + queryClass.getName());
  }

  /**
   * Creates the exception for a query that has no handler for its exact class while {@code
   * handledSuperclass} has one. Handlers match exact message classes, so that handler does not
   * receive subclasses.
   */
  public QueryNotRegisteredException(Class<?> queryClass, Class<?> handledSuperclass) {
    super(
        "No handler registered for query: "
            + queryClass.getName()
            + "; a handler is registered for its superclass "
            + handledSuperclass.getName()
            + ", but handlers match the exact message class, so it does not receive subclasses."
            + " Register a handler for "
            + queryClass.getName());
  }
}
