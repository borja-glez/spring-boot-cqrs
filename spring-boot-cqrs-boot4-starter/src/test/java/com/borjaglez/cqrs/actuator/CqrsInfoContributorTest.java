package com.borjaglez.cqrs.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerType;
import com.borjaglez.cqrs.introspection.MiddlewareDescriptor;

class CqrsInfoContributorTest {

  @Test
  void contributesCqrsSectionWithCounts() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlerCount(HandlerType.COMMAND)).thenReturn(3);
    when(introspection.getHandlerCount(HandlerType.EVENT)).thenReturn(5);
    when(introspection.getHandlerCount(HandlerType.QUERY)).thenReturn(2);
    when(introspection.getMiddleware())
        .thenReturn(List.of(new MiddlewareDescriptor(SampleMiddleware.class, 0, false)));
    when(introspection.getRegisteredMessageTypes()).thenReturn(Set.of(Object.class, String.class));

    Info.Builder builder = new Info.Builder();
    new CqrsInfoContributor(introspection).contribute(builder);

    @SuppressWarnings("unchecked")
    Map<String, Object> cqrs = (Map<String, Object>) builder.build().getDetails().get("cqrs");
    assertThat(cqrs)
        .containsEntry("commands", 3)
        .containsEntry("events", 5)
        .containsEntry("queries", 2)
        .containsEntry("middleware", 1)
        .containsEntry("messageTypes", 2);
  }

  private static final class SampleMiddleware {}
}
