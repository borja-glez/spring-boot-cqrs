package com.borjaglez.cqrs.rabbitmq.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;

@SpringBootApplication(scanBasePackages = "com.borjaglez.cqrs.rabbitmq")
@Import(CqrsAutoConfiguration.class)
public class TestApplication {}
