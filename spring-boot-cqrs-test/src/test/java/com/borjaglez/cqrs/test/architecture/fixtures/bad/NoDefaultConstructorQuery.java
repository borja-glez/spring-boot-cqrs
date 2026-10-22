package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.naming.CqrsMessage;

@CqrsMessage(service = "shop", module = "orders", name = "NoDefaultConstructorQuery")
public class NoDefaultConstructorQuery extends Query {
  public NoDefaultConstructorQuery(String value) {}
}
