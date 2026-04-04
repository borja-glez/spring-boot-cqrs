package com.borjaglez.cqrs.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonMessageSerializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JacksonMessageSerializer serializer = new JacksonMessageSerializer(objectMapper);

  @Test
  void serializeDeserializeRoundTrip() {
    TestMessage command = new TestMessage("hello", UUID.randomUUID().toString());
    byte[] bytes = serializer.serialize(command);
    TestMessage deserialized = serializer.deserialize(bytes, TestMessage.class);

    assertThat(deserialized.getData()).isEqualTo("hello");
    assertThat(deserialized.getCommandId()).isEqualTo(command.getCommandId());
  }

  @Test
  void serializeProducesValidJson() {
    TestMessage command = new TestMessage("test-data", UUID.randomUUID().toString());
    byte[] bytes = serializer.serialize(command);
    String json = new String(bytes);

    assertThat(json).contains("\"data\"").contains("test-data").contains("\"commandId\"");
  }

  @Test
  void deserializeWithWrongClassThrows() {
    TestMessage command = new TestMessage("hello", UUID.randomUUID().toString());
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

  static class TestMessage {
    private String data;
    private String commandId;

    TestMessage() {}

    TestMessage(String data, String commandId) {
      this.data = data;
      this.commandId = commandId;
    }

    public String getData() {
      return data;
    }

    public String getCommandId() {
      return commandId;
    }
  }
}
