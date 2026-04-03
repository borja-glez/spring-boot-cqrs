package com.borjaglez.cqrs.example.middleware.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.middleware.command.GreetCommand;
import com.borjaglez.cqrs.example.middleware.query.GetGreetingQuery;
import com.borjaglez.cqrs.query.QueryBus;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public GreetingController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping("/{name}")
  public String greet(@PathVariable String name) {
    return commandBus.dispatchAndReceive(new GreetCommand(name));
  }

  @GetMapping("/{name}")
  public String getGreeting(@PathVariable String name) {
    return queryBus.ask(new GetGreetingQuery(name));
  }
}
