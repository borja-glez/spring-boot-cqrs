package com.borjaglez.cqrs.actuator;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerDescriptor;
import com.borjaglez.cqrs.introspection.HandlerType;
import com.borjaglez.cqrs.introspection.MiddlewareDescriptor;

@Endpoint(id = "cqrs")
public class CqrsEndpoint {

  private final CqrsIntrospection introspection;

  public CqrsEndpoint(CqrsIntrospection introspection) {
    this.introspection = introspection;
  }

  @ReadOperation
  public CqrsDescriptor cqrs() {
    return new CqrsDescriptor(
        new HandlerCounts(
            introspection.getHandlerCount(HandlerType.COMMAND),
            introspection.getHandlerCount(HandlerType.EVENT),
            introspection.getHandlerCount(HandlerType.QUERY)),
        introspection.getHandlers().stream().map(HandlerView::from).toList(),
        introspection.getMiddleware().stream().map(MiddlewareView::from).toList(),
        introspection.getRegisteredMessageTypes().stream().map(Class::getName).sorted().toList());
  }

  @ReadOperation
  public List<HandlerView> handlersByKind(@Selector String section, @Selector String kind) {
    if (!"handlers".equals(section)) {
      throw new IllegalArgumentException("Unknown section: " + section);
    }
    HandlerType type = HandlerType.valueOf(kind.toUpperCase(Locale.ROOT));
    return introspection.getHandlers(type).stream().map(HandlerView::from).toList();
  }

  public record CqrsDescriptor(
      HandlerCounts counts,
      List<HandlerView> handlers,
      List<MiddlewareView> middleware,
      List<String> messageTypes) {}

  public record HandlerCounts(int commands, int events, int queries) {}

  public record HandlerView(
      String kind,
      String messageType,
      String messageName,
      String handlerBeanType,
      boolean requiresValidation) {

    static HandlerView from(HandlerDescriptor descriptor) {
      return new HandlerView(
          descriptor.handlerType().name().toLowerCase(Locale.ROOT),
          descriptor.messageType().getName(),
          descriptor.messageName(),
          descriptor.handlerBeanType().getName(),
          descriptor.requiresValidation());
    }
  }

  public record MiddlewareView(String type, int order, boolean observability) {

    static MiddlewareView from(MiddlewareDescriptor descriptor) {
      return new MiddlewareView(
          descriptor.middlewareType().getName(), descriptor.order(), descriptor.isObservability());
    }
  }
}
