package com.borjaglez.cqrs.serialization;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

class MessageSerializerTest {

  @Test
  void deserializeTypeDelegatesToRawClassDeserializer() {
    MessageSerializer serializer = new ClassOnlyMessageSerializer();
    byte[] bytes = "payload".getBytes(UTF_8);

    String result = serializer.deserialize(bytes, String.class);

    assertThat(result).isEqualTo("payload:String");
  }

  @Test
  void deserializeParameterizedTypeReferenceDelegatesToTypeDeserializer() {
    MessageSerializer serializer = new ClassOnlyMessageSerializer();
    byte[] bytes = "payload".getBytes(UTF_8);

    String result =
        serializer.deserialize(bytes, new ParameterizedTypeReference<String>() {});

    assertThat(result).isEqualTo("payload:String");
  }

  @Test
  void deserializeTypeFailsForGenericTypesWhenSerializerOnlySupportsRawClasses() {
    MessageSerializer serializer = new ClassOnlyMessageSerializer();
    byte[] bytes = "payload".getBytes(UTF_8);
    Type targetType = new ParameterizedTypeReference<Map<String, List<String>>>() {}.getType();

    assertThatThrownBy(() -> serializer.deserialize(bytes, targetType))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not support generic type deserialization")
        .hasMessageContaining("java.util.Map<java.lang.String, java.util.List<java.lang.String>>");
  }

  private static final class ClassOnlyMessageSerializer implements MessageSerializer {

    @Override
    public byte[] serialize(Object message) {
      return message.toString().getBytes(UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> type) {
      return (T) (new String(data, UTF_8) + ":" + type.getSimpleName());
    }
  }
}
