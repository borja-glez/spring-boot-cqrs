package com.borjaglez.cqrs.asyncapi;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.borjaglez.cqrs.asyncapi.fixtures.CheckStock;
import com.borjaglez.cqrs.asyncapi.fixtures.FindOrder;
import com.borjaglez.cqrs.asyncapi.fixtures.Jackson2TestSerializer;
import com.borjaglez.cqrs.asyncapi.fixtures.Money;
import com.borjaglez.cqrs.asyncapi.fixtures.OrderPlaced;
import com.borjaglez.cqrs.asyncapi.fixtures.OrderView;
import com.borjaglez.cqrs.asyncapi.fixtures.PlaceOrder;
import com.borjaglez.cqrs.asyncapi.fixtures.PlaceOrderWithNote;
import com.borjaglez.cqrs.asyncapi.fixtures.StockLevel;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerDescriptor;
import com.borjaglez.cqrs.introspection.HandlerType;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class AsyncApiVerifierTest {

  private static final String PLACE_ORDER = "shop.orders.1.command.order.place-order";
  private static final String ORDER_PLACED = "shop.orders.1.event.order.order-placed";
  private static final String FIND_ORDER = "shop.orders.1.query.order.find-order";
  private static final String CHECK_STOCK = "shop.inventory.1.query.stock.check-stock";

  private static final AsyncApiContract SHOP_ORDERS =
      AsyncApiContract.fromClasspath("asyncapi/shop-orders.yaml");

  @TempDir Path directory;

  @Test
  void validPayloadPasses() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample(new PlaceOrder("customer-1", Money.euros("19.90")))
            .sample(new OrderPlaced("order-1", "customer-1", Money.euros("19.90")))
            .sample(new FindOrder("order-1"))
            .sample(new CheckStock("sku-1"));

    assertThatCode(verifier::verifyPayloads).doesNotThrowAnyException();
  }

  @Test
  void extraPropertyFailsAgainstClosedSchema() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample(new PlaceOrderWithNote("customer-1", Money.euros("5"), "leave at the door"));

    assertThatThrownBy(verifier::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            "1 contract violation:\n- "
                + PLACE_ORDER
                + ": $: property 'note' is not defined in the schema and the schema does not allow"
                + " additional properties");
  }

  @Test
  void missingRequiredFieldFails() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample(PLACE_ORDER, Map.of("commandId", "command-1", "customerId", "customer-1"));

    assertThatThrownBy(verifier::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(PLACE_ORDER + ": $: required property 'total' not found");
  }

  @Test
  void reportsAllViolationsAtOnce() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample(new PlaceOrder("", Money.euros("5")))
            .sample(new OrderPlaced("order-1", "customer-1", new Money(BigDecimal.ONE, "euro")))
            .sample("order-view", new OrderView("order-1", "LOST", Money.euros("5")))
            .sample(new FindOrder("order-1"));

    assertThatThrownBy(verifier::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            String.join(
                "\n",
                "3 contract violations:",
                "- " + PLACE_ORDER + ": $.customerId: must be at least 1 characters long",
                "- "
                    + ORDER_PLACED
                    + ": $.total.currency: does not match the regex pattern"
                    + " ^[A-Z]{3}$",
                "- order-view: $.status: does not have a value in the enumeration [\"PLACED\","
                    + " \"SHIPPED\", \"CANCELLED\"]"));
  }

  @Test
  void samplesMissingFromTheContractFailPayloadVerification() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS).sample("shop.orders.1.event.order.order-cancelled", Map.of());

    assertThatThrownBy(verifier::verifyPayloads)
        .hasMessage(
            "1 contract violation:\n- shop.orders.1.event.order.order-cancelled: not declared in"
                + " the contract");
  }

  @Test
  void coverageReportsMissingAndExtraMessages() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample(new PlaceOrder("customer-1", Money.euros("5")))
            .sample(new OrderPlaced("order-1", "customer-1", Money.euros("5")))
            .sample(new FindOrder("order-1"))
            .sample("order-view", new OrderView("order-1", "PLACED", Money.euros("5")))
            .sample("place-order-result", "order-1")
            .sample("shop.orders.1.event.order.order-cancelled", Map.of())
            .sample("stock-reserved", Map.of());

    assertThatThrownBy(verifier::verifyCoverage)
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            String.join(
                "\n",
                "4 contract violations:",
                "- " + CHECK_STOCK + ": SENT on channel stockQueries but never sampled",
                "- stock-level: REPLY_RECEIVED on channel stockReplies but never sampled",
                "- shop.orders.1.event.order.order-cancelled: not declared in the contract",
                "- stock-reserved: not declared in the contract"));
  }

  @Test
  void coverageOfEveryContractMessagePasses() {
    AsyncApiVerifier verifier =
        everySample(verifier(SHOP_ORDERS)).sample(new PlaceOrder("customer-2", Money.euros("1")));

    assertThatCode(verifier::verifyCoverage).doesNotThrowAnyException();
  }

  @Test
  void closedSchemasPass() {
    // local and relative $ref chains are followed; a string reply payload is not an object
    assertThatCode(verifier(SHOP_ORDERS)::verifyClosedSchemas).doesNotThrowAnyException();
  }

  @Test
  void openSchemaFailsClosedSchemaCheck() throws IOException {
    write("schemas/open.json", "{ \"type\": \"object\" }");
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "open.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  orders:
                    messages:
                      placed:
                        name: order-placed
                        payload:
                          $ref: 'schemas/open.json'
                      shipped:
                        name: order-shipped
                        payload:
                          properties:
                            orderId:
                              type: string
                      cancelled:
                        name: order-cancelled
                        payload:
                          type: object
                          additionalProperties: true
                      reason:
                        name: cancel-reason
                        payload:
                          type: string
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/orders'
                """));

    assertThatThrownBy(verifier(contract)::verifyClosedSchemas)
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            String.join(
                "\n",
                "3 contract violations:",
                "- order-cancelled: the payload schema does not declare additionalProperties:"
                    + " false",
                "- order-placed: the payload schema does not declare additionalProperties: false",
                "- order-shipped: the payload schema does not declare additionalProperties:"
                    + " false"));
  }

  @Test
  void receivedMessageWithoutHandlerFails() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlers())
        .thenReturn(
            List.of(
                new HandlerDescriptor(
                    FindOrder.class, HandlerType.QUERY, FIND_ORDER, Object.class, false)));

    assertThatThrownBy(() -> verifier(SHOP_ORDERS).verifyHandlers(introspection))
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            "1 contract violation:\n- "
                + PLACE_ORDER
                + ": RECEIVED on channel orderCommands but no handler is registered");
  }

  @Test
  void receivedMessageWithOnlyLocalHandlersFails() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlers())
        .thenReturn(
            List.of(
                new HandlerDescriptor(
                    PlaceOrder.class, HandlerType.COMMAND, PLACE_ORDER, Object.class, true, false),
                new HandlerDescriptor(
                    FindOrder.class, HandlerType.QUERY, FIND_ORDER, Object.class, false)));

    assertThatThrownBy(() -> verifier(SHOP_ORDERS).verifyHandlers(introspection))
        .hasMessage(
            "1 contract violation:\n- "
                + PLACE_ORDER
                + ": RECEIVED on channel orderCommands but its handlers are local (remote ="
                + " false)");
  }

  @Test
  void receivedMessagesWithRemoteHandlersPass() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlers())
        .thenReturn(
            List.of(
                new HandlerDescriptor(
                    PlaceOrder.class, HandlerType.COMMAND, PLACE_ORDER, Object.class, true),
                new HandlerDescriptor(
                    FindOrder.class, HandlerType.QUERY, FIND_ORDER, Object.class, false)));

    assertThatCode(() -> verifier(SHOP_ORDERS).verifyHandlers(introspection))
        .doesNotThrowAnyException();
  }

  @Test
  void replySamplesUseExplicitNames() {
    AsyncApiVerifier verifier =
        verifier(SHOP_ORDERS)
            .sample("order-view", new OrderView("order-1", "SHIPPED", Money.euros("5")))
            .sample("stock-level", new StockLevel("sku-1", 3))
            .sample("place-order-result", "order-1");

    assertThatCode(verifier::verifyPayloads).doesNotThrowAnyException();
    assertThatThrownBy(
            verifier(SHOP_ORDERS).sample("stock-level", new StockLevel("sku-1", -1))
                ::verifyPayloads)
        .hasMessageContaining("stock-level: $.available: must have a minimum value of 0");
  }

  @Test
  void sampleRejectsObjectsThatAreNotMessages() {
    AsyncApiVerifier verifier = verifier(SHOP_ORDERS);
    StockLevel reply = new StockLevel("sku-1", 3);

    assertThatThrownBy(() -> verifier.sample(reply))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(StockLevel.class.getName())
        .hasMessageContaining("sample(name, payload)");
  }

  @Test
  void payloadThatIsNotJsonFails() {
    MessageSerializer serializer = mock(MessageSerializer.class);
    when(serializer.serialize(any())).thenReturn("<order/>".getBytes(StandardCharsets.UTF_8));
    AsyncApiVerifier verifier =
        AsyncApiVerifier.of(SHOP_ORDERS, serializer, new DefaultMessageNamingStrategy("shop"))
            .sample(new FindOrder("order-1"));

    assertThatThrownBy(verifier::verifyPayloads)
        .hasMessageStartingWith(
            "1 contract violation:\n- " + FIND_ORDER + ": the serialized payload is not JSON")
        .hasMessageNotContaining("\n at ");
  }

  @Test
  void validatesMultiFormatSchemasWithTheirJsonSchemaVersion() throws IOException {
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "multi-format.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  points:
                    messages:
                      point:
                        name: point
                        payload:
                          $ref: '#/components/schemas/point'
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/points'
                components:
                  schemas:
                    point:
                      schemaFormat: application/schema+json;version=2020-12
                      schema:
                        type: array
                        prefixItems:
                          - type: number
                          - type: number
                        items: false
                """));

    assertThatCode(verifier(contract).sample("point", List.of(1, 2))::verifyPayloads)
        .doesNotThrowAnyException();
    assertThatThrownBy(verifier(contract).sample("point", List.of(1, "two"))::verifyPayloads)
        .hasMessageContaining("point: $[1]: string found, number expected");
    assertThatCode(verifier(contract)::verifyClosedSchemas).doesNotThrowAnyException();
  }

  @Test
  void validatesSchemaFilesWithTheDialectTheyDeclare() throws IOException {
    write(
        "schemas/segment.json",
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "array",
          "prefixItems": [{ "type": "string" }, { "type": "string" }],
          "items": false
        }
        """);
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "segments.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  segments:
                    messages:
                      segment:
                        name: segment
                        payload:
                          $ref: 'schemas/segment.json'
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/segments'
                """));

    assertThatCode(verifier(contract).sample("segment", List.of("a", "b"))::verifyPayloads)
        .doesNotThrowAnyException();
    assertThatThrownBy(verifier(contract).sample("segment", List.of("a", 2))::verifyPayloads)
        .hasMessageContaining("segment: $[1]: integer found, string expected");
  }

  @Test
  void reportsSchemaFormatsThatAreNotJsonSchema() throws IOException {
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "avro.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  orders:
                    messages:
                      placed:
                        name: order-placed
                        payload:
                          schemaFormat: application/vnd.apache.avro;version=1.9.0
                          schema:
                            type: record
                            name: OrderPlaced
                            fields: []
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/orders'
                """));
    String expected =
        "1 contract violation:\n- order-placed: the payload schema format"
            + " 'application/vnd.apache.avro;version=1.9.0' is not JSON Schema";

    assertThatThrownBy(verifier(contract).sample("order-placed", Map.of())::verifyPayloads)
        .hasMessage(expected);
    assertThatThrownBy(verifier(contract)::verifyClosedSchemas).hasMessage(expected);
  }

  @Test
  void reportsPayloadSchemasThatCannotBeLoaded() throws IOException {
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "missing-schema.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  orders:
                    messages:
                      placed:
                        name: order-placed
                        payload:
                          $ref: 'schemas/missing.json'
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/orders'
                """));

    assertThatThrownBy(verifier(contract).sample("order-placed", Map.of())::verifyPayloads)
        .hasMessageStartingWith("1 contract violation:\n- order-placed: cannot load the payload")
        .hasMessageContaining("missing.json");
    assertThatThrownBy(verifier(contract)::verifyClosedSchemas)
        .hasMessageContaining("order-placed: cannot load the payload");
  }

  @Test
  void messagesWithoutPayloadAcceptAnySample() throws IOException {
    AsyncApiContract contract =
        AsyncApiContract.load(
            write(
                "no-payload.yaml",
                """
                asyncapi: 3.0.0
                channels:
                  heartbeats:
                    messages:
                      heartbeat:
                        name: heartbeat
                operations:
                  publish:
                    action: send
                    channel:
                      $ref: '#/channels/heartbeats'
                """));
    AsyncApiVerifier verifier = verifier(contract).sample("heartbeat", Map.of("at", "now"));

    assertThatCode(verifier::verifyPayloads).doesNotThrowAnyException();
    assertThatCode(verifier::verifyClosedSchemas).doesNotThrowAnyException();
  }

  private static AsyncApiVerifier everySample(AsyncApiVerifier verifier) {
    return verifier
        .sample(new PlaceOrder("customer-1", Money.euros("5")))
        .sample(new OrderPlaced("order-1", "customer-1", Money.euros("5")))
        .sample(new FindOrder("order-1"))
        .sample(new CheckStock("sku-1"))
        .sample("order-view", new OrderView("order-1", "PLACED", Money.euros("5")))
        .sample("place-order-result", "order-1")
        .sample("stock-level", new StockLevel("sku-1", 3));
  }

  private static AsyncApiVerifier verifier(AsyncApiContract contract) {
    return AsyncApiVerifier.of(
        contract, new Jackson2TestSerializer(), new DefaultMessageNamingStrategy("shop"));
  }

  private Path write(String name, String content) throws IOException {
    Path file = directory.resolve(name);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
