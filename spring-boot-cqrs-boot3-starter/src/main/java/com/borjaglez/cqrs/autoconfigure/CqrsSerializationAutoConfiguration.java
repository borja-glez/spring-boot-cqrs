package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration
// Needs the mapper that Spring Boot's Jackson auto-configuration creates.
@AutoConfigureAfter(
    value = CqrsAutoConfiguration.class,
    name = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
@ConditionalOnBean(ObjectMapper.class)
public class CqrsSerializationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(MessageSerializer.class)
  public JacksonMessageSerializer jacksonMessageSerializer(ObjectMapper objectMapper) {
    return new JacksonMessageSerializer(objectMapper);
  }
}
