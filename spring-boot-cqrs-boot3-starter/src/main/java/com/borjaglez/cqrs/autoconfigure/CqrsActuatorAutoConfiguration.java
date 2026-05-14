package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.actuator.CqrsEndpoint;
import com.borjaglez.cqrs.actuator.CqrsInfoContributor;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;

@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnBean(CqrsIntrospection.class)
public class CqrsActuatorAutoConfiguration {

  @Bean
  @ConditionalOnAvailableEndpoint(endpoint = CqrsEndpoint.class)
  @ConditionalOnMissingBean
  public CqrsEndpoint cqrsEndpoint(CqrsIntrospection introspection) {
    return new CqrsEndpoint(introspection);
  }

  @Bean
  @ConditionalOnEnabledInfoContributor("cqrs")
  @ConditionalOnMissingBean
  public CqrsInfoContributor cqrsInfoContributor(CqrsIntrospection introspection) {
    return new CqrsInfoContributor(introspection);
  }
}
