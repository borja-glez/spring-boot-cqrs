package com.borjaglez.cqrs.example.basic.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Task {

  private final String id;
  private String title;
  private String description;
  private boolean completed;

  public Task(String id, String title, String description) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.completed = false;
  }
}
