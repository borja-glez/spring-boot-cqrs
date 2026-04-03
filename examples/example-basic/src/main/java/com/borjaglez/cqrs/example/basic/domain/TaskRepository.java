package com.borjaglez.cqrs.example.basic.domain;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

  void save(Task task);

  Optional<Task> findById(String id);

  List<Task> findAll();

  void deleteById(String id);
}
