package com.borjaglez.cqrs.aot.scanned;

import java.util.List;

import com.borjaglez.cqrs.event.Event;

/** An event the application only records; no local handler declares it. */
public class OrderPlaced extends Event {

  private List<OrderLine> lines;

  public List<OrderLine> getLines() {
    return lines;
  }
}
