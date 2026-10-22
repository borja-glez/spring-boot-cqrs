package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

@CqrsMessage(service = "shop", module = "orders", name = "GoodQuery")
public class GoodQuery extends Query {
  public GoodQuery() {}
}
