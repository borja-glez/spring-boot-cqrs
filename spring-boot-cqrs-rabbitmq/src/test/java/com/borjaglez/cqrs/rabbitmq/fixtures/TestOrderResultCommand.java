package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Answered with a {@code TestResult<TestOrder>}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "test", module = "order", name = "place")
public class TestOrderResultCommand extends Command {

  private String orderId;
}
