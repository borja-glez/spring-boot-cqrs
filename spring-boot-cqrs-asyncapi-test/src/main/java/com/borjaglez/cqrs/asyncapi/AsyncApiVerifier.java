package com.borjaglez.cqrs.asyncapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerDescriptor;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies the messages of an application against an {@link AsyncApiContract}.
 *
 * <p>Register one sample instance per message with {@link #sample(Object)} (commands, queries and
 * events, named by the {@link MessageNamingStrategy}) or {@link #sample(String, Object)} (replies
 * and other payloads named by the contract), then run the verifications. Each verification throws a
 * single {@link AssertionError} that lists every violation it found, one per line, prefixed by the
 * message name.
 */
public final class AsyncApiVerifier {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String NOT_IN_CONTRACT = "not declared in the contract";

  private final AsyncApiContract contract;
  private final MessageSerializer serializer;
  private final MessageNamingStrategy naming;
  private final List<Sample> samples = new ArrayList<>();

  private AsyncApiVerifier(
      AsyncApiContract contract, MessageSerializer serializer, MessageNamingStrategy naming) {
    this.contract = contract;
    this.serializer = serializer;
    this.naming = naming;
  }

  /**
   * Creates a verifier.
   *
   * @param contract the contract to verify against
   * @param serializer the serializer the application sends messages with
   * @param naming the naming strategy that gives commands, queries and events their logical name
   */
  public static AsyncApiVerifier of(
      AsyncApiContract contract, MessageSerializer serializer, MessageNamingStrategy naming) {
    return new AsyncApiVerifier(
        Objects.requireNonNull(contract, "contract"),
        Objects.requireNonNull(serializer, "serializer"),
        Objects.requireNonNull(naming, "naming"));
  }

  /**
   * Adds a sample command, query or event, named by the naming strategy.
   *
   * @throws IllegalArgumentException when the message is not a command, query or event
   */
  public AsyncApiVerifier sample(Object message) {
    return sample(logicalName(Objects.requireNonNull(message, "message")), message);
  }

  /** Adds a sample payload under the name the contract gives it, such as a reply. */
  public AsyncApiVerifier sample(String name, Object payload) {
    samples.add(new Sample(Objects.requireNonNull(name, "name"), payload));
    return this;
  }

  /**
   * Checks that every sample, once serialized, is valid against the payload schema of the contract
   * message with its name. A sample whose name the contract does not declare is a violation.
   */
  public void verifyPayloads() {
    List<String> violations = new ArrayList<>();
    for (Sample sample : samples) {
      if (contract.message(sample.name()).isEmpty()) {
        violations.add(sample.name() + ": " + NOT_IN_CONTRACT);
      } else {
        payloadViolations(sample)
            .forEach(violation -> violations.add(sample.name() + ": " + violation));
      }
    }
    fail(violations);
  }

  /** Checks that the samples and the contract declare the same message names. */
  public void verifyCoverage() {
    Set<String> sampled =
        samples.stream().map(Sample::name).collect(Collectors.toCollection(TreeSet::new));
    List<String> violations = new ArrayList<>();
    contract.messages().stream()
        .filter(message -> !sampled.contains(message.name()))
        .map(message -> describe(message) + " but never sampled")
        .forEach(violations::add);
    sampled.stream()
        .filter(name -> contract.message(name).isEmpty())
        .map(name -> name + ": " + NOT_IN_CONTRACT)
        .forEach(violations::add);
    fail(violations);
  }

  /**
   * Checks that every object payload schema of the contract declares {@code additionalProperties:
   * false}, after following its top-level {@code $ref}s, so that a field added in Java but not in
   * the contract fails {@link #verifyPayloads()}.
   */
  public void verifyClosedSchemas() {
    List<String> violations = new ArrayList<>();
    contract.messages().stream()
        .map(ContractMessage::name)
        .distinct()
        .sorted()
        .forEach(
            name ->
                contract
                    .openPayloadSchemas(name)
                    .forEach(violation -> violations.add(name + ": " + violation)));
    fail(violations);
  }

  /**
   * Checks that every {@link MessageRole#RECEIVED} message of the contract has a handler that can
   * receive it from a remote transport (not marked {@code remote = false}).
   */
  public void verifyHandlers(CqrsIntrospection introspection) {
    List<HandlerDescriptor> handlers = introspection.getHandlers();
    fail(
        contract.messages().stream()
            .filter(message -> message.role() == MessageRole.RECEIVED)
            .map(message -> handlerViolation(message, handlers))
            .flatMap(Optional::stream)
            .toList());
  }

  private String logicalName(Object message) {
    if (message instanceof Command) {
      return naming.commandName(message.getClass());
    }
    if (message instanceof Query) {
      return naming.queryName(message.getClass());
    }
    if (message instanceof Event) {
      return naming.eventName(message.getClass());
    }
    throw new IllegalArgumentException(
        message.getClass().getName()
            + " is not a Command, Query or Event; add it with sample(name, payload)");
  }

  private List<String> payloadViolations(Sample sample) {
    JsonNode payload;
    try {
      payload = JSON.readTree(serializer.serialize(sample.payload()));
    } catch (IOException e) {
      return List.of(
          "the serialized payload is not JSON: " + e.getMessage().lines().findFirst().orElse(""));
    }
    return contract.payloadViolations(sample.name(), payload);
  }

  private static Optional<String> handlerViolation(
      ContractMessage message, List<HandlerDescriptor> handlers) {
    List<HandlerDescriptor> matching =
        handlers.stream().filter(handler -> message.name().equals(handler.messageName())).toList();
    if (matching.isEmpty()) {
      return Optional.of(describe(message) + " but no handler is registered");
    }
    if (matching.stream().noneMatch(HandlerDescriptor::remote)) {
      return Optional.of(describe(message) + " but its handlers are local (remote = false)");
    }
    return Optional.empty();
  }

  private static String describe(ContractMessage message) {
    return message.name() + ": " + message.role() + " on channel " + message.channel();
  }

  private static void fail(List<String> violations) {
    if (!violations.isEmpty()) {
      String heading =
          violations.size()
              + (violations.size() == 1 ? " contract violation:" : " contract violations:");
      throw new AssertionError(
          violations.stream()
              .map(violation -> "\n- " + violation)
              .collect(Collectors.joining("", heading, "")));
    }
  }

  private record Sample(String name, Object payload) {}
}
