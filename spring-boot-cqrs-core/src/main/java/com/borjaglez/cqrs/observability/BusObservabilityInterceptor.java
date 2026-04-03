package com.borjaglez.cqrs.observability;

import com.borjaglez.cqrs.middleware.BusMiddleware;

/**
 * Marker interface for observability middleware implementations. Extends {@link BusMiddleware} for
 * type discrimination when configuring the middleware pipeline.
 */
public interface BusObservabilityInterceptor extends BusMiddleware {}
