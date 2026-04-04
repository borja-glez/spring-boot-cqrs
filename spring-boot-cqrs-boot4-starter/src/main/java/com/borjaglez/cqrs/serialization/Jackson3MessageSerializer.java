package com.borjaglez.cqrs.serialization;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link MessageSerializer} implementation backed by Jackson 3. This serializer uses {@link
 * JsonMapper} and is intended for use with Spring Boot 4 which defaults to Jackson 3.
 */
public class Jackson3MessageSerializer implements MessageSerializer {

  private final JsonMapper jsonMapper;

  public Jackson3MessageSerializer(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public byte[] serialize(Object message) {
    return jsonMapper.writeValueAsBytes(message);
  }

  @Override
  public <T> T deserialize(byte[] data, Class<T> type) {
    return jsonMapper.readValue(data, type);
  }
}
