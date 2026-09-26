package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.command.Command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Answered with a {@code TestResult<TestOrder>}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestOrderResultCommand extends Command {

  private String orderId;
}
