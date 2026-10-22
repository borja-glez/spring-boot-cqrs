package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

@CqrsMessage(service = "shop", module = "orders", name = "GoodEvent")
public class GoodEvent extends Event {
  public GoodEvent() {}
}
