package com.borjaglez.cqrs.autoconfigure;

import jakarta.validation.Validator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.validation.CommandValidationInterceptor;

@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnClass(name = "jakarta.validation.Validator")
@ConditionalOnProperty(
    prefix = "cqrs.validation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CqrsValidationAutoConfiguration {

  @Bean
  @ConditionalOnBean(Validator.class)
  public CommandValidationInterceptor commandValidationInterceptor(Validator validator) {
    return new CommandValidationInterceptor(validator);
  }
}
