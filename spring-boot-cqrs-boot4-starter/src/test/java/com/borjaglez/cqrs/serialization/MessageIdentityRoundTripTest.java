package com.borjaglez.cqrs.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.query.Query;

import tools.jackson.databind.json.JsonMapper;

/**
 * Messages keep their identity on the wire: consumers deduplicate by {@code eventId} and trace by
 * {@code commandId}, so a deserialized message must carry the ids it was sent with (finding C21).
 */
class MessageIdentityRoundTripTest {

  private final Jackson3MessageSerializer serializer =
      new Jackson3MessageSerializer(JsonMapper.builder().build());

  @Test
  void eventKeepsItsIdAndOccurrenceTime() {
    SampleEvent sent = new SampleEvent("order-1");

    SampleEvent received = serializer.deserialize(serializer.serialize(sent), SampleEvent.class);

    assertThat(received.getEventId()).isEqualTo(sent.getEventId());
    assertThat(received.getOccurredOn()).isEqualTo(sent.getOccurredOn());
    assertThat(received.getOrderId()).isEqualTo("order-1");
    assertThat(received).isEqualTo(sent);
  }

  @Test
  void commandKeepsItsId() {
    SampleCommand sent = new SampleCommand("order-1");

    SampleCommand received =
        serializer.deserialize(serializer.serialize(sent), SampleCommand.class);

    assertThat(received.getCommandId()).isEqualTo(sent.getCommandId());
  }

  @Test
  void queryKeepsItsId() {
    SampleQuery sent = new SampleQuery("order-1");

    SampleQuery received = serializer.deserialize(serializer.serialize(sent), SampleQuery.class);

    assertThat(received.getQueryId()).isEqualTo(sent.getQueryId());
  }

  public static class SampleEvent extends Event {
    private String orderId;

    protected SampleEvent() {}

    public SampleEvent(String orderId) {
      this.orderId = orderId;
    }

    public String getOrderId() {
      return orderId;
    }
  }

  public static class SampleCommand extends Command {
    private String orderId;

    protected SampleCommand() {}

    public SampleCommand(String orderId) {
      this.orderId = orderId;
    }

    public String getOrderId() {
      return orderId;
    }
  }

  public static class SampleQuery extends Query {
    private String orderId;

    protected SampleQuery() {}

    public SampleQuery(String orderId) {
      this.orderId = orderId;
    }

    public String getOrderId() {
      return orderId;
    }
  }
}
