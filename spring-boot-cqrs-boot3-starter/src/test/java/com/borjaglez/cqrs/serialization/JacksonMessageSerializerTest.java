package com.borjaglez.cqrs.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

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
  void deserializeSupportsParameterizedTypeReference() {
    byte[] bytes = serializer.serialize(List.of(new TestMessage("hello", UUID.randomUUID().toString())));

    List<TestMessage> deserialized =
        serializer.deserialize(bytes, new ParameterizedTypeReference<List<TestMessage>>() {});

    assertThat(deserialized).hasSize(1);
    assertThat(deserialized.getFirst().getData()).isEqualTo("hello");
  }

  @Test
  void deserializeSupportsNestedParameterizedTypeReference() {
    TestMessage firstMessage = new TestMessage("hello", UUID.randomUUID().toString());
    TestMessage secondMessage = new TestMessage("world", UUID.randomUUID().toString());
    byte[] bytes =
        serializer.serialize(Map.of("items", List.of(firstMessage, secondMessage)));

    Map<String, List<TestMessage>> deserialized =
        serializer.deserialize(
            bytes, new ParameterizedTypeReference<Map<String, List<TestMessage>>>() {});

    assertThat(deserialized).containsOnlyKeys("items");
    assertThat(deserialized.get("items"))
        .extracting(TestMessage::getData)
        .containsExactly("hello", "world");
    assertThat(deserialized.get("items"))
        .extracting(TestMessage::getCommandId)
        .containsExactly(firstMessage.getCommandId(), secondMessage.getCommandId());
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

  @Test
  void deserializeByTypeThrowsUncheckedIOExceptionOnFailure() throws Exception {
    ObjectMapper brokenMapper = mock(ObjectMapper.class);
    Type targetType = new ParameterizedTypeReference<List<TestMessage>>() {}.getType();
    when(brokenMapper.getTypeFactory()).thenReturn(TypeFactory.defaultInstance());
    when(brokenMapper.readValue(any(byte[].class), any(JavaType.class)))
        .thenThrow(new JsonProcessingException("deserialize fail") {});

    JacksonMessageSerializer brokenSerializer = new JacksonMessageSerializer(brokenMapper);

    assertThatThrownBy(() -> brokenSerializer.deserialize("[]".getBytes(), targetType))
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
