package com.borjaglez.cqrs.naming;

public interface MessageNamingStrategy {

  String commandName(Class<?> commandClass);

  String eventName(Class<?> eventClass);

  String queryName(Class<?> queryClass);
}
