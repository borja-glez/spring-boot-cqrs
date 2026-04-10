package com.borjaglez.cqrs.introspection;

public record HandlerDescriptor(
    Class<?> messageType,
    HandlerType handlerType,
    String messageName,
    Class<?> handlerBeanType,
    boolean requiresValidation) {}
