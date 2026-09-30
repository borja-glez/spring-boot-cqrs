package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitTemplateFactory.BootTemplateTypes;

class RabbitTemplateFactoryTest {

  private static final BootTemplateTypes MISSING =
      new BootTemplateTypes("com.example.MissingConfigurer", "com.example.MissingCustomizer");

  private static final BootTemplateTypes FAKE =
      new BootTemplateTypes(FakeConfigurer.class.getName(), FakeCustomizer.class.getName());

  private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

  @Test
  void configuresTheTemplateWithTheFirstBootLineOnTheClasspath() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("configurer", new FakeConfigurer());
    List<String> customized = new ArrayList<>();
    beanFactory.addBean("first", (FakeCustomizer) template -> customized.add("first"));
    beanFactory.addBean("second", (FakeCustomizer) template -> customized.add("second"));

    RabbitTemplate template = create(beanFactory, List.of(MISSING, FAKE), Duration.ofMillis(750));

    assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    assertThat(template.getMessageConverter()).isInstanceOf(SimpleMessageConverter.class);
    assertThat(template.getRoutingKey()).isEqualTo("configured");
    assertThat(customized).containsExactlyInAnyOrder("first", "second");
    // The configurer set another reply timeout: the one of the bus wins.
    assertThat(ReflectionTestUtils.getField(template, "replyTimeout")).isEqualTo(750L);
  }

  @Test
  void usesTheBoot3ConfigurerAndCustomizersInThisModule() {
    RabbitProperties properties = new RabbitProperties();
    properties.getTemplate().setMandatory(true);
    properties.getTemplate().setObservationEnabled(true);
    properties.getTemplate().setReplyTimeout(Duration.ofSeconds(30));
    properties.getTemplate().setExchange("configured-exchange");
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("configurer", new RabbitTemplateConfigurer(properties));
    beanFactory.addBean(
        "customizer", (RabbitTemplateCustomizer) template -> template.setRoutingKey("customized"));

    RabbitTemplate template =
        RabbitTemplateFactory.create(beanFactory, connectionFactory, Duration.ofSeconds(2));

    assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    assertThat(template.getExchange()).isEqualTo("configured-exchange");
    assertThat(template.getRoutingKey()).isEqualTo("customized");
    assertThat(ReflectionTestUtils.getField(template, "observationEnabled")).isEqualTo(true);
    assertThat(template.isMandatoryFor(null)).isTrue();
    assertThat(ReflectionTestUtils.getField(template, "replyTimeout")).isEqualTo(2000L);
  }

  @Test
  void looksForTheBoot4TypesFirstAndThenTheBoot3Ones() {
    assertThat(RabbitTemplateFactory.BOOT_TEMPLATE_TYPES)
        .containsExactly(
            new BootTemplateTypes(
                "org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer",
                "org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer"),
            new BootTemplateTypes(
                RabbitTemplateConfigurer.class.getName(),
                RabbitTemplateCustomizer.class.getName()));
  }

  @Test
  void failsWithoutAConfigurerOnTheClasspath() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();

    assertThatThrownBy(() -> create(beanFactory, List.of(MISSING), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RabbitTemplateConfigurer, which is not on the classpath");
  }

  @Test
  void failsWithoutAConfigurerBean() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();

    assertThatThrownBy(() -> create(beanFactory, List.of(FAKE), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(FakeConfigurer.class.getName() + " bean");
  }

  @Test
  void rethrowsTheRuntimeExceptionOfTheConfigurer() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("configurer", new RuntimeFailingConfigurer());
    BootTemplateTypes types =
        new BootTemplateTypes(
            RuntimeFailingConfigurer.class.getName(), FakeCustomizer.class.getName());

    assertThatThrownBy(() -> create(beanFactory, List.of(types), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid template settings");
  }

  @Test
  void wrapsTheCheckedExceptionOfTheConfigurer() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("configurer", new CheckedFailingConfigurer());
    BootTemplateTypes types =
        new BootTemplateTypes(
            CheckedFailingConfigurer.class.getName(), FakeCustomizer.class.getName());

    assertThatThrownBy(() -> create(beanFactory, List.of(types), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to configure the RabbitTemplate of a bus")
        .cause()
        .hasMessage("checked failure");
  }

  @Test
  void failsWhenTheCustomizerTypeCannotBeUsed() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("configurer", new FakeConfigurer());
    BootTemplateTypes types =
        new BootTemplateTypes(FakeConfigurer.class.getName(), NotACustomizer.class.getName());

    assertThatThrownBy(() -> create(beanFactory, List.of(types), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to configure the RabbitTemplate of a bus")
        .hasCauseInstanceOf(NoSuchMethodException.class);
  }

  private RabbitTemplate create(
      StaticListableBeanFactory beanFactory,
      List<BootTemplateTypes> candidates,
      Duration replyTimeout) {
    return RabbitTemplateFactory.create(
        getClass().getClassLoader(), candidates, beanFactory, connectionFactory, replyTimeout);
  }

  /** Stands for Boot's {@code RabbitTemplateConfigurer}. */
  public static class FakeConfigurer {

    public void configure(RabbitTemplate template, ConnectionFactory connectionFactory) {
      template.setConnectionFactory(connectionFactory);
      template.setRoutingKey("configured");
      template.setReplyTimeout(5000);
    }
  }

  /** Stands for Boot's {@code RabbitTemplateCustomizer}. */
  public interface FakeCustomizer {

    void customize(RabbitTemplate template);
  }

  public static class RuntimeFailingConfigurer {

    public void configure(RabbitTemplate template, ConnectionFactory connectionFactory) {
      throw new IllegalArgumentException("invalid template settings");
    }
  }

  public static class CheckedFailingConfigurer {

    public void configure(RabbitTemplate template, ConnectionFactory connectionFactory)
        throws Exception {
      throw new Exception("checked failure");
    }
  }

  public static class NotACustomizer {}
}
