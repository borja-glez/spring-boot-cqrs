package com.borjaglez.cqrs.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonMessageSerializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JacksonMessageSerializer serializer = new JacksonMessageSerializer(objectMapper);

  @Test
  void serializeDeserializeRoundTrip() {
    TestCommand command = new TestCommand("hello");
    byte[] bytes = serializer.serialize(command);
    TestCommand deserialized = serializer.deserialize(bytes, TestCommand.class);

    assertThat(deserialized.getData()).isEqualTo("hello");
    assertThat(deserialized.getCommandId()).isEqualTo(command.getCommandId());
  }

  @Test
  void serializeProducesValidJson() {
    TestCommand command = new TestCommand("test-data");
    byte[] bytes = serializer.serialize(command);
    String json = new String(bytes);

    assertThat(json).contains("\"data\"").contains("test-data").contains("\"commandId\"");
  }

  @Test
  void deserializeWithWrongClassThrows() {
    TestCommand command = new TestCommand("hello");
    byte[] bytes = serializer.serialize(command);

    assertThatThrownBy(() -> serializer.deserialize(bytes, Integer.class))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void serializeThrowsUncheckedIOExceptionOnFailure() throws Exception {
    ObjectMapper brokenMapper = mock(ObjectMapper.class);
    when(brokenMapper.writeValueAsBytes(any()))
        .thenThrow(new JsonProcessingException("serialize fail") {});

    JacksonMessageSerializer brokenSerializer = new JacksonMessageSerializer(brokenMapper);

    assertThatThrownBy(() -> brokenSerializer.serialize("anything"))
        .isInstanceOf(UncheckedIOException.class);
  }
}
