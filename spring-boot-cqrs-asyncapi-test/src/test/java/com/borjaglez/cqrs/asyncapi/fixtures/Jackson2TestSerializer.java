package com.borjaglez.cqrs.asyncapi.fixtures;

import java.time.Instant;

import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Serializes like the Boot 3 starter: JSON through Jackson 2, instants as ISO-8601 strings. */
public class Jackson2TestSerializer implements MessageSerializer {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(
              new SimpleModule().addSerializer(Instant.class, ToStringSerializer.instance));

  @Override
  public byte[] serialize(Object message) {
    try {
      return mapper.writeValueAsBytes(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <T> T deserialize(byte[] data, Class<T> type) {
    throw new UnsupportedOperationException("The contract verifier only serializes");
  }
}
