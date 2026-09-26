package com.borjaglez.cqrs.aot.scanned;

import com.borjaglez.cqrs.command.Command;

/** A command the application only sends to another service. */
public class ChargeCard extends Command {

  private long cents;

  public long getCents() {
    return cents;
  }
}
