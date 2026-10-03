package com.borjaglez.cqrs.jdbc.outbox;

import java.time.Clock;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Contributes the transactional outbox when {@code cqrs.outbox.enabled=true}, the application has a
 * single {@link DataSource} and transaction manager, and the CQRS starter is present: {@link
 * OutboxEventBus} (never primary), the outbox table, and, unless {@code
 * cqrs.outbox.relay.enabled=false}, the relay publishing through the transport's event bus. Runs
 * after the starters' auto-configuration so {@code springEventBus} is defined first.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
      "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
      "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
      "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration",
      "com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration"
    })
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBooleanProperty("cqrs.outbox.enabled")
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties({JdbcCqrsProperties.class, OutboxProperties.class})
@ImportRuntimeHints(OutboxRuntimeHints.class)
public class CqrsJdbcOutboxAutoConfiguration {

  private static final Log LOG = LogFactory.getLog(CqrsJdbcOutboxAutoConfiguration.class);

  /** Nested so the transaction manager must be a single candidate too. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
  @ConditionalOnBean({
    EventHandlerRegistry.class,
    MessageSerializer.class,
    MessageNamingStrategy.class
  })
  static class JdbcOutboxConfiguration {

    /** Declared first: member classes are processed before the bean methods below. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class MicrometerTracingConfiguration {

      @Bean
      @ConditionalOnMissingBean
      OutboxTracing outboxTracing(
          ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        Tracer uniqueTracer = tracer.getIfUnique();
        Propagator uniquePropagator = propagator.getIfUnique();
        if (uniqueTracer == null || uniquePropagator == null) {
          return OutboxTracing.noop();
        }
        return new MicrometerOutboxTracing(uniqueTracer, uniquePropagator);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(name = "cqrs.outbox.relay.enabled", matchIfMissing = true)
    static class RelayConfiguration {

      @Bean
      @ConditionalOnMissingBean
      OutboxRelay outboxRelay(
          OutboxStore store,
          OutboxEventTypeResolver resolver,
          MessageSerializer serializer,
          OutboxContextCodec contextCodec,
          ListableBeanFactory beanFactory,
          PlatformTransactionManager transactionManager,
          OutboxProperties properties,
          Environment environment) {
        OutboxProperties.Relay relay = properties.getRelay();
        EventBus target =
            OutboxTargetBus.resolve(
                beanFactory.getBeansOfType(EventBus.class), relay.getEventBus());
        if (OutboxTargetBus.publishesWithoutConfirms(target, environment)) {
          LOG.warn(
              "The outbox relays through RabbitMQ without publisher confirms: rows are marked"
                  + " published before the broker accepted them. Set"
                  + " cqrs.rabbitmq.events.confirms.enabled=true and"
                  + " spring.rabbitmq.publisher-confirm-type=correlated");
        }
        return new OutboxRelay(
            store,
            resolver,
            serializer,
            contextCodec,
            target,
            transactionManager,
            relay.getBatchSize(),
            relay.getMaxAttempts());
      }

      @Bean
      @ConditionalOnMissingBean
      OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay, OutboxProperties properties) {
        return new OutboxRelayScheduler(relay, properties.getRelay().getInterval());
      }
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxTracing noopOutboxTracing() {
      return OutboxTracing.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxSchemaInitializer outboxSchemaInitializer(
        DataSource dataSource, JdbcCqrsProperties properties) {
      return new OutboxSchemaInitializer(
          dataSource, properties.getInitializeSchema(), properties.getOutbox().getTableName());
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxStore outboxStore(
        DataSource dataSource,
        JdbcCqrsProperties properties,
        ObjectProvider<OutboxSchemaInitializer> schemaInitializer) {
      // Create the table before anything can write to it.
      schemaInitializer.ifAvailable(initializer -> {});
      return new OutboxStore(dataSource, properties.getOutbox().getTableName());
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxContextCodec outboxContextCodec(MessageSerializer serializer, OutboxTracing tracing) {
      return new OutboxContextCodec(serializer, tracing);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxEventBus outboxEventBus(
        OutboxStore store,
        MessageSerializer serializer,
        MessageNamingStrategy naming,
        OutboxContextCodec contextCodec) {
      return new OutboxEventBus(store, serializer, naming, contextCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxEventTypeResolver outboxEventTypeResolver(
        EventHandlerRegistry registry,
        MessageNamingStrategy naming,
        Environment environment,
        ResourceLoader resourceLoader) {
      return new OutboxEventTypeResolver(
          registry,
          naming,
          OutboxEventTypeResolver.packages(environment),
          resourceLoader.getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBooleanProperty(name = "cqrs.jdbc.outbox.cleanup-enabled", matchIfMissing = true)
    OutboxCleanup outboxCleanup(
        OutboxStore store, OutboxProperties properties, JdbcCqrsProperties jdbcProperties) {
      return new OutboxCleanup(
          store,
          properties.getRetention(),
          jdbcProperties.getOutbox().getCleanupInterval(),
          Clock.systemUTC());
    }
  }
}
