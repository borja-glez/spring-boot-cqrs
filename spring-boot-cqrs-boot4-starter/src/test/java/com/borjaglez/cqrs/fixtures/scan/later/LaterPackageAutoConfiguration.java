package com.borjaglez.cqrs.fixtures.scan.later;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

import com.borjaglez.cqrs.autoconfigure.CqrsHandlersAutoConfiguration;

/**
 * Adds its package to the auto-configuration packages after the handler auto-configuration, as the
 * auto-configuration of a module with its own JPA repositories or entities does.
 */
@AutoConfiguration(after = CqrsHandlersAutoConfiguration.class)
@AutoConfigurationPackage
public class LaterPackageAutoConfiguration {}
