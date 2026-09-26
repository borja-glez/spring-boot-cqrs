package com.borjaglez.cqrs.kafka.fixtures;

/** A generic wrapper result: its runtime class says nothing about {@code T}. */
public record TestResult<T>(T value) {}
