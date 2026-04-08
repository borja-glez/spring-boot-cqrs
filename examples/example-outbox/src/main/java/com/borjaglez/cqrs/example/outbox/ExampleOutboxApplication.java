package com.borjaglez.cqrs.example.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ExampleOutboxApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExampleOutboxApplication.class, args);
  }
}
