package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs")
public class CqrsProperties {
  private NamingProperties naming = new NamingProperties();
  private ValidationProperties validation = new ValidationProperties();
  private ObservabilityProperties observability = new ObservabilityProperties();

  @Getter
  @Setter
  public static class NamingProperties {
    private String prefix = "";
  }

  @Getter
  @Setter
  public static class ValidationProperties {
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class ObservabilityProperties {
    private boolean enabled = true;
  }
}
