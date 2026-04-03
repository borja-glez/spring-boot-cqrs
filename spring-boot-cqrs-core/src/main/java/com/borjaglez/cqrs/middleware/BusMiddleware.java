package com.borjaglez.cqrs.middleware;

@FunctionalInterface
public interface BusMiddleware {

  Object process(Object message, MiddlewareChain chain) throws Exception;
}
