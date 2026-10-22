package com.borjaglez.cqrs.asyncapi;

import static com.borjaglez.cqrs.asyncapi.MessageRole.RECEIVED;
import static com.borjaglez.cqrs.asyncapi.MessageRole.REPLY_RECEIVED;
import static com.borjaglez.cqrs.asyncapi.MessageRole.REPLY_SENT;
import static com.borjaglez.cqrs.asyncapi.MessageRole.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.borjaglez.cqrs.asyncapi.fixtures.Jackson2TestSerializer;
import com.borjaglez.cqrs.asyncapi.fixtures.Money;
import com.borjaglez.cqrs.asyncapi.fixtures.PlaceOrder;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;

class AsyncApiContractTest {

  private static final String SHOP_ORDERS = "asyncapi/shop-orders.yaml";
  private static final String PLACE_ORDER = "shop.orders.1.command.order.place-order";

  @TempDir Path directory;

  @Test
  void readsSendReceiveAndReplyMessages() {
    AsyncApiContract contract = AsyncApiContract.fromClasspath(SHOP_ORDERS);

    assertThat(contract.messages())
        .containsExactly(
            new ContractMessage(PLACE_ORDER, RECEIVED, "orderCommands"),
            new ContractMessage("shop.orders.1.event.order.order-placed", SENT, "orderEvents"),
            new ContractMessage("shop.orders.1.query.order.find-order", RECEIVED, "orderQueries"),
            new ContractMessage("order-view", REPLY_SENT, "orderQueryReplies"),
            new ContractMessage("place-order-result", REPLY_SENT, "placeOrderReplies"),
            new ContractMessage("shop.inventory.1.query.stock.check-stock", SENT, "stockQueries"),
            new ContractMessage("stock-level", REPLY_RECEIVED, "stockReplies"));
  }

  @Test
  void rejectsMessageWithoutName() throws IOException {
    Path document =
        write(
            "unnamed.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orderEvents:
                messages:
                  orderPlaced:
                    payload:
                      type: object
            operations:
              publish:
                action: send
                channel:
                  $ref: '#/channels/orderEvents'
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#/channels/orderEvents/messages/orderPlaced")
        .hasMessageContaining("name");
  }

  @Test
  void findsMessageByName() {
    AsyncApiContract contract = AsyncApiContract.fromClasspath(SHOP_ORDERS);

    assertThat(contract.message("stock-level"))
        .contains(new ContractMessage("stock-level", REPLY_RECEIVED, "stockReplies"));
    assertThat(contract.message("shop.orders.1.event.order.order-cancelled")).isEmpty();
  }

  @Test
  void loadsFromPath() throws URISyntaxException {
    Path document =
        Path.of(Objects.requireNonNull(getClass().getResource("/" + SHOP_ORDERS)).toURI());

    AsyncApiContract contract = AsyncApiContract.load(document);

    assertThat(contract.messages())
        .isEqualTo(AsyncApiContract.fromClasspath(SHOP_ORDERS).messages());
    assertThatCode(() -> verifier(contract).sample(validOrder()).verifyPayloads())
        .doesNotThrowAnyException();
  }

  @Test
  void resolvesRelativeSchemaRefsFromClasspath() {
    AsyncApiContract contract = AsyncApiContract.fromClasspath(SHOP_ORDERS);

    // place-order.json references total in its sibling definitions.json
    assertThatCode(() -> verifier(contract).sample(validOrder()).verifyPayloads())
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                verifier(contract)
                    .sample(new PlaceOrder("customer-1", new Money(BigDecimal.TEN, "eur")))
                    .verifyPayloads())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(PLACE_ORDER + ": $.total.currency: does not match the regex pattern");
  }

  @Test
  void resolvesRelativeSchemaRefsFromAPath() throws IOException {
    write("my contracts/schemas/money.json", MONEY_SCHEMA);
    write(
        "my contracts/schemas/order.json",
        """
        {
          "type": "object",
          "additionalProperties": false,
          "properties": { "commandId": {}, "customerId": {}, "total": { "$ref": "money.json" } }
        }
        """);
    Path document =
        write(
            "my contracts/contract.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders:
                messages:
                  placeOrder:
                    name: shop.orders.1.command.order.place-order
                    payload:
                      $ref: 'schemas/order.json'
            operations:
              receive:
                action: receive
                channel:
                  $ref: '#/channels/orders'
            """);

    AsyncApiVerifier verifier = verifier(AsyncApiContract.load(document));

    assertThatCode(() -> verifier.sample(validOrder()).verifyPayloads()).doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                verifier
                    .sample(new PlaceOrder("customer-1", new Money(BigDecimal.ONE, "eur")))
                    .verifyPayloads())
        .hasMessageContaining("$.total.currency: does not match the regex pattern");
  }

  @Test
  void listsAMessageOncePerNameAndRole() throws IOException {
    Path document =
        write(
            "duplicates.yaml",
            """
            asyncapi: 3.0.1
            channels:
              primary:
                messages:
                  placed:
                    $ref: '#/components/messages/placed'
              secondary:
                messages:
                  placed:
                    $ref: '#/components/messages/placed'
            operations:
              receiveFromSecondary:
                action: receive
                channel:
                  $ref: '#/channels/secondary'
              receiveFromPrimary:
                action: receive
                channel:
                  $ref: '#/channels/primary'
              sendToPrimary:
                action: send
                channel:
                  $ref: '#/channels/primary'
                reply:
                  address:
                    location: '$message.header#/replyTo'
            components:
              messages:
                placed:
                  name: order-placed
            """);

    assertThat(AsyncApiContract.load(document).messages())
        .containsExactly(
            new ContractMessage("order-placed", SENT, "primary"),
            new ContractMessage("order-placed", RECEIVED, "primary"));
  }

  @Test
  void readsOperationsAndChannelsDeclaredAsReferences() throws IOException {
    Path document =
        write(
            "references.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders:
                $ref: '#/components/channels/orderChannel'
            operations:
              receive:
                $ref: '#/components/operations/receive'
            components:
              channels:
                orderChannel:
                  messages:
                    placeOrder:
                      name: place-order
              operations:
                receive:
                  action: receive
                  channel:
                    $ref: '#/channels/orders'
                  messages:
                    - $ref: '#/channels/orders/messages/placeOrder'
            """);

    assertThat(AsyncApiContract.load(document).messages())
        .containsExactly(new ContractMessage("place-order", RECEIVED, "orders"));
  }

  @Test
  void rejectsDocumentsThatAreNotAsyncApi3() throws IOException {
    Path document = write("v2.yaml", "asyncapi: 2.6.0\nchannels: {}\n");

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AsyncAPI 3")
        .hasMessageContaining("2.6.0");
  }

  @Test
  void rejectsUnknownOperationAction() throws IOException {
    Path document =
        write(
            "action.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders: {}
            operations:
              publish:
                action: publish
                channel:
                  $ref: '#/channels/orders'
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("publish")
        .hasMessageContaining("send or receive");
  }

  @Test
  void rejectsOperationWithoutChannelReference() throws IOException {
    Path document =
        write(
            "no-channel.yaml",
            """
            asyncapi: 3.0.0
            operations:
              publish:
                action: send
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("publish")
        .hasMessageContaining("#/channels/<channel>");
  }

  @Test
  void rejectsOperationMessagesOutsideTheirChannel() throws IOException {
    Path document =
        write(
            "components-message.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders: {}
            operations:
              publish:
                action: send
                channel:
                  $ref: '#/channels/orders'
                messages:
                  - $ref: '#/components/messages/placed'
            components:
              messages:
                placed:
                  name: order-placed
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#/components/messages/placed")
        .hasMessageContaining("#/channels/<channel>/messages/<message>");
  }

  @Test
  void rejectsReferencesToOtherDocumentsOutsidePayloads() throws IOException {
    Path document =
        write(
            "external.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders:
                messages:
                  placed:
                    $ref: 'messages.yaml#/placed'
            operations:
              publish:
                action: send
                channel:
                  $ref: '#/channels/orders'
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("messages.yaml#/placed");
  }

  @Test
  void rejectsUnresolvableReferences() throws IOException {
    Path document =
        write(
            "unresolvable.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders:
                messages:
                  placed:
                    $ref: '#/components/messages/missing'
            operations:
              publish:
                action: send
                channel:
                  $ref: '#/channels/orders'
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#/components/messages/missing")
        .hasMessageContaining("not defined");
  }

  @Test
  void rejectsCircularReferences() throws IOException {
    Path document =
        write(
            "circular.yaml",
            """
            asyncapi: 3.0.0
            channels:
              orders:
                messages:
                  placed:
                    $ref: '#/components/messages/first'
            operations:
              publish:
                action: send
                channel:
                  $ref: '#/channels/orders'
            components:
              messages:
                first:
                  $ref: '#/components/messages/second'
                second:
                  $ref: '#/components/messages/first'
            """);

    assertThatThrownBy(() -> AsyncApiContract.load(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Circular");
  }

  @Test
  void rejectsMissingDocuments() {
    assertThatThrownBy(() -> AsyncApiContract.fromClasspath("asyncapi/missing.yaml"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("classpath:asyncapi/missing.yaml");
  }

  private static final String MONEY_SCHEMA =
      """
      {
        "type": "object",
        "additionalProperties": false,
        "properties": {
          "amount": { "type": "number" },
          "currency": { "type": "string", "pattern": "^[A-Z]{3}$" }
        }
      }
      """;

  private static PlaceOrder validOrder() {
    return new PlaceOrder("customer-1", Money.euros("19.90"));
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
