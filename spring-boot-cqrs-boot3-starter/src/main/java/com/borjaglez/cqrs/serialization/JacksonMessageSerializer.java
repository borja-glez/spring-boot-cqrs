package com.borjaglez.cqrs.serialization;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link MessageSerializer} implementation backed by Jackson 2. This serializer uses {@link
 * ObjectMapper} and is intended for use with Spring Boot 3.
 */
public class JacksonMessageSerializer implements MessageSerializer {

  private final ObjectMapper objectMapper;

  public JacksonMessageSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public byte[] serialize(Object message) {
    try {
      return objectMapper.writeValueAsBytes(message);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public <T> T deserialize(byte[] data, Class<T> type) {
    try {
      return objectMapper.readValue(data, type);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public <T> T deserialize(byte[] data, Type type) {
    try {
      return objectMapper.readValue(data, objectMapper.getTypeFactory().constructType(type));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
