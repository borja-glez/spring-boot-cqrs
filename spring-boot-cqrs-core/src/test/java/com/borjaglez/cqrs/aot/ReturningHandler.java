package com.borjaglez.cqrs.aot;

import java.util.List;

import com.borjaglez.cqrs.aot.scanned.OrderLine;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
class ReturningHandler {

  static class Reserve extends Command {
    private List<OrderLine> lines;

    public List<OrderLine> getLines() {
      return lines;
    }
  }

  record Reservation(boolean reserved, List<String> missing) {}

  @HandleCommand
  Reservation reserve(Reserve command) {
    return new Reservation(true, List.of());
  }
}
