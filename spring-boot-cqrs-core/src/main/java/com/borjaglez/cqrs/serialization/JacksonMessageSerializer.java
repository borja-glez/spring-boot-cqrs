package com.borjaglez.cqrs.serialization;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default {@link MessageSerializer} implementation backed by Jackson. This serializer is intended
 * to be used when Jackson is available on the classpath.
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
}
