package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

@CqrsMessage(service = "shop", module = "orders", name = "NoDefaultConstructorQuery")
public class NoDefaultConstructorQuery extends Query {
  public NoDefaultConstructorQuery(String value) {}
}
