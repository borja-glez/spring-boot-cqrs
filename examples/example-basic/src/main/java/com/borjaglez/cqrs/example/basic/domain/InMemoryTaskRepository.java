package com.borjaglez.cqrs.example.basic.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTaskRepository implements TaskRepository {

  private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

  @Override
  public void save(Task task) {
    tasks.put(task.getId(), task);
  }

  @Override
  public Optional<Task> findById(String id) {
    return Optional.ofNullable(tasks.get(id));
  }

  @Override
  public List<Task> findAll() {
    return new ArrayList<>(tasks.values());
  }

  @Override
  public void deleteById(String id) {
    tasks.remove(id);
  }
}
