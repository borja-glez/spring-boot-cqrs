package com.borjaglez.cqrs.rabbitmq.fixtures;

/** A generic wrapper result: its runtime class says nothing about {@code T}. */
public record TestResult<T>(T value) {}
