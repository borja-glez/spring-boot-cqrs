package com.borjaglez.cqrs.actuator;

import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerType;

public class CqrsInfoContributor implements InfoContributor {

  private final CqrsIntrospection introspection;

  public CqrsInfoContributor(CqrsIntrospection introspection) {
    this.introspection = introspection;
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail(
        "cqrs",
        Map.of(
            "commands", introspection.getHandlerCount(HandlerType.COMMAND),
            "events", introspection.getHandlerCount(HandlerType.EVENT),
            "queries", introspection.getHandlerCount(HandlerType.QUERY),
            "middleware", introspection.getMiddleware().size(),
            "messageTypes", introspection.getRegisteredMessageTypes().size()));
  }
}
