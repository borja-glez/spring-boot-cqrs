package com.borjaglez.cqrs.fixtures.scan;

import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.discovery.EnableCqrsHandlers;

/** Scans its own package, as {@code @EnableCqrsHandlers} does without attributes. */
@Configuration(proxyBeanMethods = false)
@EnableCqrsHandlers
public class ScannedHandlersConfiguration {}
