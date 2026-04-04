package com.borjaglez.cqrs.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class Jackson3MessageSerializerTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final Jackson3MessageSerializer serializer = new Jackson3MessageSerializer(jsonMapper);

  @Test
  void serializeDeserializeRoundTrip() {
    TestData data = new TestData("hello", 42);
    byte[] bytes = serializer.serialize(data);
    TestData deserialized = serializer.deserialize(bytes, TestData.class);

    assertThat(deserialized.name).isEqualTo("hello");
    assertThat(deserialized.value).isEqualTo(42);
  }

  @Test
  void serializeProducesValidJson() {
    TestData data = new TestData("test-data", 1);
    byte[] bytes = serializer.serialize(data);
    String json = new String(bytes);

    assertThat(json).contains("\"name\"").contains("test-data").contains("\"value\"");
  }

  @Test
  void deserializeWithWrongClassThrows() {
    TestData data = new TestData("hello", 1);
    byte[] bytes = serializer.serialize(data);

    assertThatThrownBy(() -> serializer.deserialize(bytes, Integer.class))
        .isInstanceOf(JacksonException.class);
  }

  public static class TestData {
    public String name;
    public int value;

    public TestData() {}

    public TestData(String name, int value) {
      this.name = name;
      this.value = value;
    }
  }
}
