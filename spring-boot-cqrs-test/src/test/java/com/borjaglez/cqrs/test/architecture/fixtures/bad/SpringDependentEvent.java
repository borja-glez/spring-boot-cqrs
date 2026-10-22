package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;
import org.springframework.util.StringUtils;

@CqrsMessage(service = "shop", module = "orders", name = "SpringDependentEvent")
public class SpringDependentEvent extends Event {
  public SpringDependentEvent() {}

  boolean check(String value) {
    return StringUtils.hasText(value);
  }
}
