package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.serialization.Jackson3MessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;

import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnClass(name = "tools.jackson.databind.json.JsonMapper")
@ConditionalOnBean(JsonMapper.class)
public class CqrsSerializationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(MessageSerializer.class)
  public Jackson3MessageSerializer jacksonMessageSerializer(JsonMapper jsonMapper) {
    return new Jackson3MessageSerializer(jsonMapper);
  }
}
