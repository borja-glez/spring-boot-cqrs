package com.borjaglez.cqrs.asyncapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.AnnotationKeyword;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.RefValidator;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.DefaultSchemaLoader;
import com.networknt.schema.resource.SchemaLoader;

/**
 * The messages an AsyncAPI 3 document declares for the service that owns it.
 *
 * <p>Each operation contributes the messages it lists, or every message of its channel when it
 * lists none: a {@code send} operation contributes {@link MessageRole#SENT} messages and a {@code
 * receive} operation {@link MessageRole#RECEIVED} ones. The messages of an operation's {@code
 * reply} are {@link MessageRole#REPLY_SENT} for a {@code receive} and {@link
 * MessageRole#REPLY_RECEIVED} for a {@code send}. The {@code name} of every Message Object is
 * required: it is the logical name the application gives the message.
 *
 * <p>References between operations, channels and messages must point inside the document. Payload
 * schemas may reference other files: a relative {@code $ref} resolves against the location of the
 * document, both for {@link #load(Path)} and {@link #fromClasspath(String)}.
 */
public final class AsyncApiContract {

  private static final ObjectMapper YAML = new YAMLMapper();
  private static final SchemaLoader LOADER = new DefaultSchemaLoader(List.of(), List.of());

  private static final JsonPointer OPERATIONS = JsonPointer.compile("/operations");
  private static final JsonPointer MESSAGES = JsonPointer.compile("/messages");
  private static final Pattern CHANNEL_REF = Pattern.compile("#(/channels/[^/]+)");
  private static final Pattern MESSAGE_REF = Pattern.compile("#(/channels/[^/]+)(/messages/[^/]+)");

  private static final Map<String, List<MessageRole>> ROLES_BY_ACTION =
      Map.of(
          "send", List.of(MessageRole.SENT, MessageRole.REPLY_RECEIVED),
          "receive", List.of(MessageRole.RECEIVED, MessageRole.REPLY_SENT));

  // An AsyncAPI Schema Object is a superset of JSON Schema draft-07.
  private static final String DEFAULT_SCHEMA_FORMAT = "application/vnd.aai.asyncapi;version=3.0.0";
  private static final String ASYNCAPI_SCHEMA_FORMAT = "application/vnd.aai.asyncapi";
  private static final Pattern JSON_SCHEMA_FORMAT =
      Pattern.compile("application/schema\\+(?:json|yaml);version=(.+)");
  private static final Map<String, JsonMetaSchema> DIALECTS =
      Map.of(
          "draft-04", JsonMetaSchema.getV4(),
          "draft-06", JsonMetaSchema.getV6(),
          "draft-07", JsonMetaSchema.getV7(),
          "2019-09", JsonMetaSchema.getV201909(),
          "2020-12", JsonMetaSchema.getV202012());
  // The document is read as a schema too, so its AsyncAPI fields are unknown keywords: keep them as
  // annotations without the warning the validator logs for each one.
  private static final List<JsonMetaSchema> QUIET_DIALECTS =
      DIALECTS.values().stream()
          .map(
              dialect ->
                  JsonMetaSchema.builder(dialect)
                      .unknownKeywordFactory((keyword, context) -> new AnnotationKeyword(keyword))
                      .build())
          .toList();
  // English messages whatever the default locale, with instance paths such as $.total.currency.
  private static final SchemaValidatorsConfig CONFIG =
      SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).pathType(PathType.JSON_PATH).build();

  private final String location;
  private final JsonNode document;
  private final List<ContractMessage> messages;
  private final Map<String, Set<Payload>> payloads = new HashMap<>();
  private final Map<String, JsonSchema> documentSchemas = new ConcurrentHashMap<>();

  private AsyncApiContract(String location, JsonNode document) {
    this.location = location;
    this.document = document;
    String version = document.path("asyncapi").asText();
    if (!version.startsWith("3.")) {
      throw new IllegalArgumentException(
          location + " is not an AsyncAPI 3 document (asyncapi: '" + version + "')");
    }
    List<Declaration> declarations = new ArrayList<>();
    document
        .path("operations")
        .properties()
        .forEach(operation -> declareOperation(operation.getKey(), declarations));
    declarations.sort(
        Comparator.comparing((Declaration declaration) -> declaration.message().channel())
            .thenComparing(declaration -> declaration.message().name())
            .thenComparing(declaration -> declaration.message().role()));
    Map<String, ContractMessage> unique = new LinkedHashMap<>();
    for (Declaration declaration : declarations) {
      ContractMessage message = declaration.message();
      unique.putIfAbsent(message.name() + '\u0000' + message.role(), message);
      if (declaration.payload() != null) {
        payloads
            .computeIfAbsent(message.name(), name -> new LinkedHashSet<>())
            .add(declaration.payload());
      }
    }
    this.messages = List.copyOf(unique.values());
  }

  /**
   * Reads the AsyncAPI 3 document (YAML or JSON) at the given path.
   *
   * @throws IllegalArgumentException when the document cannot be read or is not a valid contract
   */
  public static AsyncApiContract load(Path document) {
    return read(document.toAbsolutePath().toUri().toString());
  }

  /**
   * Reads the AsyncAPI 3 document (YAML or JSON) at the given classpath resource, for example
   * {@code asyncapi/orders.yaml}.
   *
   * @throws IllegalArgumentException when the document cannot be read or is not a valid contract
   */
  public static AsyncApiContract fromClasspath(String resource) {
    return read("classpath:" + resource);
  }

  /** Every message of the contract once per name and role, ordered by channel and name. */
  public List<ContractMessage> messages() {
    return messages;
  }

  /** The first message of {@link #messages()} with the given name. */
  public Optional<ContractMessage> message(String name) {
    return messages.stream().filter(message -> message.name().equals(name)).findFirst();
  }

  /** Validates a payload against every payload schema the contract declares for the name. */
  List<String> payloadViolations(String name, JsonNode payload) {
    return check(
        name,
        schema ->
            schema.validate(payload).stream().map(ValidationMessage::getMessage).sorted().toList());
  }

  /** Reports the object payload schemas of the name that do not reject unknown properties. */
  List<String> openPayloadSchemas(String name) {
    return check(
        name,
        schema ->
            isOpenObject(dereference(schema).getSchemaNode())
                ? List.of("the payload schema does not declare additionalProperties: false")
                : List.of());
  }

  private static AsyncApiContract read(String location) {
    try (InputStream input = LOADER.getSchema(AbsoluteIri.of(location)).getInputStream()) {
      return new AsyncApiContract(location, YAML.readTree(input));
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read the AsyncAPI document " + location, e);
    }
  }

  private void declareOperation(String id, List<Declaration> declarations) {
    Located operation = locate(OPERATIONS.appendProperty(id));
    String context = "Operation '" + id + "'";
    String action = operation.node().path("action").asText();
    List<MessageRole> roles = ROLES_BY_ACTION.get(action);
    if (roles == null) {
      throw new IllegalArgumentException(
          context + " has action '" + action + "'; expected send or receive");
    }
    Channel channel = channel(operation.node().get("channel"), context);
    declareMessages(operation.node().get("messages"), channel, roles.get(0), context, declarations);
    JsonNode reply = operation.node().get("reply");
    if (reply != null) {
      Located resolved = locate(operation.pointer().appendProperty("reply"));
      JsonNode replyChannel = resolved.node().get("channel");
      declareMessages(
          resolved.node().get("messages"),
          replyChannel == null ? null : channel(replyChannel, context + " reply"),
          roles.get(1),
          context + " reply",
          declarations);
    }
  }

  private void declareMessages(
      JsonNode references,
      Channel channel,
      MessageRole role,
      String context,
      List<Declaration> declarations) {
    if (references != null) {
      for (JsonNode reference : references) {
        declareListedMessage(reference.path("$ref").asText(), role, context, declarations);
      }
    } else if (channel != null) {
      channel
          .definition()
          .node()
          .path("messages")
          .properties()
          .forEach(
              message ->
                  declarations.add(declaration(channel, channel.message(message.getKey()), role)));
    }
  }

  private void declareListedMessage(
      String reference, MessageRole role, String context, List<Declaration> declarations) {
    Matcher matcher = MESSAGE_REF.matcher(reference);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          context
              + " lists '"
              + reference
              + "'; reference messages as #/channels/<channel>/messages/<message>");
    }
    Channel channel = channel(JsonPointer.compile(matcher.group(1)));
    JsonPointer message =
        channel.definition().pointer().append(JsonPointer.compile(matcher.group(2)));
    declarations.add(declaration(channel, message, role));
  }

  private Channel channel(JsonNode reference, String context) {
    String target = reference == null ? "" : reference.path("$ref").asText();
    Matcher matcher = CHANNEL_REF.matcher(target);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          context + " must reference its channel as #/channels/<channel>, found '" + target + "'");
    }
    return channel(JsonPointer.compile(matcher.group(1)));
  }

  private Channel channel(JsonPointer pointer) {
    // The key comes from the reference: the channel itself may be a reference to components.
    return new Channel(pointer.last().getMatchingProperty(), locate(pointer));
  }

  private Declaration declaration(Channel channel, JsonPointer messagePointer, MessageRole role) {
    Located message = locate(messagePointer);
    String name = message.node().path("name").asText();
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "AsyncAPI message #"
              + message.pointer()
              + " has no name; set it to the logical name of the message");
    }
    return new Declaration(new ContractMessage(name, role, channel.key()), payload(message));
  }

  private Payload payload(Located message) {
    if (!message.node().has("payload")) {
      return null;
    }
    // References inside the document are followed so that a Multi Format Schema Object kept in
    // components is recognized; a reference to another file is left to the schema validator.
    Located payload = follow(message.pointer().appendProperty("payload"));
    if (payload.node().has("schemaFormat")) {
      return new Payload(
          payload.pointer().appendProperty("schema"), payload.node().path("schemaFormat").asText());
    }
    return new Payload(payload.pointer(), DEFAULT_SCHEMA_FORMAT);
  }

  /** Finds the node at the pointer, following references, which must stay inside the document. */
  private Located locate(JsonPointer pointer) {
    Located located = follow(pointer);
    if (located.node().has("$ref")) {
      throw new IllegalArgumentException(
          "#"
              + located.pointer()
              + " references '"
              + located.node().get("$ref").asText()
              + "': only payload schemas may reference other documents");
    }
    return located;
  }

  /** Follows references inside the document up to a node that is not one of them. */
  private Located follow(JsonPointer pointer) {
    Set<JsonPointer> visited = new HashSet<>();
    JsonPointer current = pointer;
    while (true) {
      JsonNode node = document.at(current);
      if (node.isMissingNode()) {
        throw new IllegalArgumentException("#" + current + " is not defined in " + location);
      }
      String reference = node.path("$ref").asText();
      if (!reference.startsWith("#")) {
        return new Located(current, node);
      }
      if (!visited.add(current)) {
        throw new IllegalArgumentException("Circular reference at #" + current + " in " + location);
      }
      current = JsonPointer.compile(reference.substring(1));
    }
  }

  private List<String> check(String name, Function<JsonSchema, List<String>> check) {
    List<String> violations = new ArrayList<>();
    for (Payload payload : payloads.getOrDefault(name, Set.of())) {
      Optional<JsonMetaSchema> dialect = dialect(payload.schemaFormat());
      if (dialect.isEmpty()) {
        violations.add(
            "the payload schema format '" + payload.schemaFormat() + "' is not JSON Schema");
        continue;
      }
      try {
        violations.addAll(check.apply(schema(payload.pointer(), dialect.get())));
      } catch (JsonSchemaException e) {
        violations.add(
            "cannot load the payload schema at #" + payload.pointer() + ": " + e.getMessage());
      }
    }
    return violations;
  }

  private JsonSchema schema(JsonPointer pointer, JsonMetaSchema dialect) {
    // The whole document is the schema resource, so that a payload can reference its components
    // and its relative references resolve against the document location.
    JsonSchema root =
        documentSchemas.computeIfAbsent(
            dialect.getIri(),
            iri ->
                JsonSchemaFactory.builder()
                    .defaultMetaSchemaIri(iri)
                    .metaSchemas(QUIET_DIALECTS)
                    .build()
                    .getSchema(SchemaLocation.of(location), document, CONFIG));
    JsonNodePath fragment = new JsonNodePath(PathType.JSON_POINTER);
    for (JsonPointer segment = pointer; !segment.matches(); segment = segment.tail()) {
      fragment = fragment.append(segment.getMatchingProperty());
    }
    return root.getRefSchema(fragment);
  }

  private static Optional<JsonMetaSchema> dialect(String schemaFormat) {
    String format = schemaFormat.replace(" ", "").toLowerCase(Locale.ROOT);
    if (format.startsWith(ASYNCAPI_SCHEMA_FORMAT)) {
      return Optional.of(DIALECTS.get("draft-07"));
    }
    Matcher matcher = JSON_SCHEMA_FORMAT.matcher(format);
    return matcher.matches()
        ? Optional.ofNullable(DIALECTS.get(matcher.group(1)))
        : Optional.empty();
  }

  private static JsonSchema dereference(JsonSchema schema) {
    JsonSchema current = schema;
    while (current.getSchemaNode().has("$ref")) {
      current =
          current.getValidators().stream()
              .filter(RefValidator.class::isInstance)
              .map(RefValidator.class::cast)
              .findFirst()
              .orElseThrow()
              .getSchemaRef()
              .getSchema();
    }
    return current;
  }

  private static boolean isOpenObject(JsonNode schema) {
    boolean object = "object".equals(schema.path("type").asText()) || schema.has("properties");
    return object && !BooleanNode.FALSE.equals(schema.get("additionalProperties"));
  }

  private record Located(JsonPointer pointer, JsonNode node) {}

  private record Channel(String key, Located definition) {

    JsonPointer message(String key) {
      return definition.pointer().append(MESSAGES).appendProperty(key);
    }
  }

  private record Payload(JsonPointer pointer, String schemaFormat) {}

  private record Declaration(ContractMessage message, Payload payload) {}
}
