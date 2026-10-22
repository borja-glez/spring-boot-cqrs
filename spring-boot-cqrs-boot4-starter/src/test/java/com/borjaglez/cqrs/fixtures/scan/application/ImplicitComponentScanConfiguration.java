package com.borjaglez.cqrs.fixtures.scan.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Component scan of its own package, as {@code @SpringBootApplication} declares it. Handlers are
 * not components, so only the CQRS handler scan can register them.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan
public class ImplicitComponentScanConfiguration {}
