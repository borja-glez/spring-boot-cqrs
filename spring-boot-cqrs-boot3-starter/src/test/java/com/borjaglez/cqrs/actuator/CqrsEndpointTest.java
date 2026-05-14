package com.borjaglez.cqrs.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.actuator.CqrsEndpoint.CqrsDescriptor;
import com.borjaglez.cqrs.actuator.CqrsEndpoint.HandlerView;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerDescriptor;
import com.borjaglez.cqrs.introspection.HandlerType;
import com.borjaglez.cqrs.introspection.MiddlewareDescriptor;

class CqrsEndpointTest {

  private CqrsIntrospection introspection;
  private CqrsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    introspection = mock(CqrsIntrospection.class);
    endpoint = new CqrsEndpoint(introspection);
  }

  @Test
  void cqrsReturnsCountsHandlersMiddlewareAndMessageTypes() {
    HandlerDescriptor command =
        new HandlerDescriptor(
            CreateOrderCommand.class, HandlerType.COMMAND, "create_order", Handler.class, true);
    HandlerDescriptor event =
        new HandlerDescriptor(
            OrderCreatedEvent.class, HandlerType.EVENT, "order_created", Handler.class, false);
    MiddlewareDescriptor middleware = new MiddlewareDescriptor(SampleMiddleware.class, 10, true);

    when(introspection.getHandlerCount(HandlerType.COMMAND)).thenReturn(1);
    when(introspection.getHandlerCount(HandlerType.EVENT)).thenReturn(1);
    when(introspection.getHandlerCount(HandlerType.QUERY)).thenReturn(0);
    when(introspection.getHandlers()).thenReturn(List.of(command, event));
    when(introspection.getMiddleware()).thenReturn(List.of(middleware));
    when(introspection.getRegisteredMessageTypes())
        .thenReturn(Set.of(CreateOrderCommand.class, OrderCreatedEvent.class));

    CqrsDescriptor descriptor = endpoint.cqrs();

    assertThat(descriptor.counts().commands()).isEqualTo(1);
    assertThat(descriptor.counts().events()).isEqualTo(1);
    assertThat(descriptor.counts().queries()).isEqualTo(0);
    assertThat(descriptor.handlers()).hasSize(2);
    assertThat(descriptor.middleware()).hasSize(1);
    assertThat(descriptor.middleware().get(0))
        .satisfies(
            view -> {
              assertThat(view.type()).isEqualTo(SampleMiddleware.class.getName());
              assertThat(view.order()).isEqualTo(10);
              assertThat(view.observability()).isTrue();
            });
    assertThat(descriptor.messageTypes())
        .containsExactly(CreateOrderCommand.class.getName(), OrderCreatedEvent.class.getName());
  }

  @Test
  void handlersByKindFiltersByCommand() {
    HandlerDescriptor command =
        new HandlerDescriptor(
            CreateOrderCommand.class, HandlerType.COMMAND, "create_order", Handler.class, true);
    when(introspection.getHandlers(HandlerType.COMMAND)).thenReturn(List.of(command));

    List<HandlerView> views = endpoint.handlersByKind("handlers", "command");

    assertThat(views)
        .singleElement()
        .satisfies(view -> assertThat(view.kind()).isEqualTo("command"));
  }

  @Test
  void handlersByKindAcceptsUpperCaseKind() {
    when(introspection.getHandlers(HandlerType.QUERY)).thenReturn(List.of());

    List<HandlerView> views = endpoint.handlersByKind("handlers", "QUERY");

    assertThat(views).isEmpty();
  }

  @Test
  void handlersByKindRejectsUnknownSection() {
    assertThatThrownBy(() -> endpoint.handlersByKind("foo", "command"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown section: foo");
  }

  @Test
  void handlersByKindRejectsUnknownKind() {
    assertThatThrownBy(() -> endpoint.handlersByKind("handlers", "bogus"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void handlerViewMapsAllFields() {
    HandlerDescriptor descriptor =
        new HandlerDescriptor(
            CreateOrderCommand.class, HandlerType.COMMAND, "create_order", Handler.class, true);

    HandlerView view = HandlerView.from(descriptor);

    assertThat(view.kind()).isEqualTo("command");
    assertThat(view.messageType()).isEqualTo(CreateOrderCommand.class.getName());
    assertThat(view.messageName()).isEqualTo("create_order");
    assertThat(view.handlerBeanType()).isEqualTo(Handler.class.getName());
    assertThat(view.requiresValidation()).isTrue();
  }

  private static final class CreateOrderCommand {}

  private static final class OrderCreatedEvent {}

  private static final class Handler {}

  private static final class SampleMiddleware {}
}
