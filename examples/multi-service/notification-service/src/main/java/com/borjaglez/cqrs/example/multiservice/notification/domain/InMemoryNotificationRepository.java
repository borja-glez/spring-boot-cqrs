package com.borjaglez.cqrs.example.multiservice.notification.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

@Component
public class InMemoryNotificationRepository implements NotificationRepository {

  private final ConcurrentLinkedDeque<Notification> store = new ConcurrentLinkedDeque<>();

  @Override
  public void save(Notification notification) {
    store.addFirst(notification);
  }

  @Override
  public List<Notification> findAll() {
    return new ArrayList<>(store);
  }
}
