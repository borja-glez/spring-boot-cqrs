package com.borjaglez.cqrs.serialization;

public interface MessageSerializer {

  byte[] serialize(Object message);

  <T> T deserialize(byte[] data, Class<T> type);
}
