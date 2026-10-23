package com.borjaglez.cqrs.asyncapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.borjaglez.cqrs.asyncapi.fixtures.Jackson2TestSerializer;
import com.borjaglez.cqrs.asyncapi.fixtures.Money;
import com.borjaglez.cqrs.asyncapi.fixtures.PlaceOrder;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;

/**
 * Runs the verifier on a test classpath shared with another json-schema-validator 2.x user, the
 * Atlassian OpenAPI validator, as in a service that checks its REST and its messaging contracts in
 * the same test suite.
 */
class JsonSchemaValidatorClasspathTest {

  private static final String PLACE_ORDER = "shop.orders.1.command.order.place-order";
  private static final String POM_PROPERTIES =
      "/META-INF/maven/com.networknt/json-schema-validator/pom.properties";
  private static final String ORDERS_API =
      """
      openapi: 3.0.3
      info:
        title: Orders
        version: 1.0.0
      paths:
        /orders/{orderId}:
          get:
            parameters:
              - name: orderId
                in: path
                required: true
                schema:
                  type: string
            responses:
              '200':
                description: The order
                content:
                  application/json:
                    schema:
                      type: object
                      required: [orderId]
                      properties:
                        orderId:
                          type: string
      """;

  @Test
  void resolvesJsonSchemaValidator2() throws IOException {
    Properties pom = new Properties();
    try (InputStream input = getClass().getResourceAsStream(POM_PROPERTIES)) {
      assertThat(input).as("%s on the test classpath", POM_PROPERTIES).isNotNull();
      pom.load(input);
    }

    assertThat(pom.getProperty("version")).startsWith("2.");
  }

  @Test
  void verifiesPayloadsNextToAnOpenApiValidator() {
    ValidationReport report =
        OpenApiInteractionValidator.createForInlineApiSpecification(ORDERS_API)
            .build()
            .validateResponse(
                "/orders/order-1",
                Request.Method.GET,
                SimpleResponse.Builder.ok()
                    .withContentType("application/json")
                    .withBody("{\"orderId\": 1}")
                    .build());
    AsyncApiContract contract = AsyncApiContract.fromClasspath("asyncapi/shop-orders.yaml");

    assertThat(report.getMessages())
        .extracting(ValidationReport.Message::getKey)
        .containsExactly("validation.response.body.schema.type");
    assertThatCode(
            verifier(contract).sample(new PlaceOrder("customer-1", Money.euros("19.90")))
                ::verifyPayloads)
        .doesNotThrowAnyException();
    assertThatThrownBy(
            verifier(contract)
                    .sample(new PlaceOrder("customer-1", new Money(BigDecimal.TEN, "eur")))
                ::verifyPayloads)
        .isInstanceOf(AssertionError.class)
        .hasMessage(
            "1 contract violation:\n- "
                + PLACE_ORDER
                + ": $.total.currency: does not match the regex pattern ^[A-Z]{3}$");
  }

  private static AsyncApiVerifier verifier(AsyncApiContract contract) {
    return AsyncApiVerifier.of(
        contract, new Jackson2TestSerializer(), new DefaultMessageNamingStrategy("shop"));
  }
}
