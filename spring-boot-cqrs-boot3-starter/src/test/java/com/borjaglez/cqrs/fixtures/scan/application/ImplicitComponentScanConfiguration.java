package com.borjaglez.cqrs.fixtures.scan.application;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.command.annotation.CommandHandler;

/**
 * Component scan of its own package, as {@code @SpringBootApplication} declares it. Command
 * handlers are excluded so that only the CQRS handler scan can register them.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(excludeFilters = @Filter(CommandHandler.class))
public class ImplicitComponentScanConfiguration {}
