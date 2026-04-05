package com.borjaglez.cqrs.example.multiservice.inventory.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.example.multiservice.inventory.domain.InMemoryProductRepository;
import com.borjaglez.cqrs.example.multiservice.inventory.domain.Product;

@CommandHandler
public class AddProductCommandHandler {

  private final InMemoryProductRepository repository;

  public AddProductCommandHandler(InMemoryProductRepository repository) {
    this.repository = repository;
  }

  @HandleCommand
  public String handle(AddProductCommand command) {
    Product product =
        new Product(command.getProductId(), command.getName(), command.getInitialStock());
    repository.save(product);
    return command.getProductId();
  }
}
