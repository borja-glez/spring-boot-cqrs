package com.borjaglez.cqrs.naming;

public class DefaultMessageNamingStrategy implements MessageNamingStrategy {

  private final String prefix;

  public DefaultMessageNamingStrategy(String prefix) {
    this.prefix = prefix == null ? "" : prefix;
  }

  @Override
  public String commandName(Class<?> commandClass) {
    return resolveName(commandClass, "command");
  }

  @Override
  public String eventName(Class<?> eventClass) {
    return resolveName(eventClass, "event");
  }

  @Override
  public String queryName(Class<?> queryClass) {
    return resolveName(queryClass, "query");
  }

  private String resolveName(Class<?> clazz, String type) {
    CqrsMessage annotation = clazz.getAnnotation(CqrsMessage.class);
    if (annotation != null) {
      return buildAnnotatedName(annotation, type);
    }
    return toKebabCase(clazz.getSimpleName());
  }

  private String buildAnnotatedName(CqrsMessage annotation, String type) {
    StringBuilder sb = new StringBuilder();
    if (!prefix.isEmpty()) {
      sb.append(prefix).append('.');
    }
    sb.append(annotation.service())
        .append('.')
        .append(annotation.version())
        .append('.')
        .append(type)
        .append('.')
        .append(annotation.module())
        .append('.')
        .append(annotation.name());
    return sb.toString();
  }

  private static String toKebabCase(String name) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        if (sb.length() > 0) {
          sb.append('-');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
