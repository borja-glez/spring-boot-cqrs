package com.borjaglez.cqrs.serialization;

import java.lang.reflect.Type;

import org.springframework.core.ParameterizedTypeReference;

public interface MessageSerializer {

  byte[] serialize(Object message);

  <T> T deserialize(byte[] data, Class<T> type);

  @SuppressWarnings("unchecked")
  default <T> T deserialize(byte[] data, Type type) {
    if (type instanceof Class<?> clazz) {
      return (T) deserialize(data, (Class<?>) clazz);
    }
    throw new IllegalArgumentException(
        "This serializer does not support generic type deserialization: " + type.getTypeName());
  }

  default <T> T deserialize(byte[] data, ParameterizedTypeReference<T> type) {
    return deserialize(data, type.getType());
  }
}
