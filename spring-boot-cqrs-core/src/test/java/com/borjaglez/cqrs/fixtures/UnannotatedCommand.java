package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.Command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnannotatedCommand extends Command {
  private String value;
}
