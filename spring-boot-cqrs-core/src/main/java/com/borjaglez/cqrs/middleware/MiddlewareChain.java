package com.borjaglez.cqrs.middleware;

public interface MiddlewareChain {

  Object proceed(Object message) throws Exception;
}
