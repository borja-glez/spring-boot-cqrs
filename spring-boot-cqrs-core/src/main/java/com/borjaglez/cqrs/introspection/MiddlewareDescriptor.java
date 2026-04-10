package com.borjaglez.cqrs.introspection;

public record MiddlewareDescriptor(Class<?> middlewareType, int order, boolean isObservability) {}
