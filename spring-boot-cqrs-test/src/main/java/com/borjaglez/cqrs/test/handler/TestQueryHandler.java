package com.borjaglez.cqrs.test.handler;

import com.borjaglez.cqrs.query.Query;

@FunctionalInterface
public interface TestQueryHandler<Q extends Query, R> {

  R handle(Q query);
}
