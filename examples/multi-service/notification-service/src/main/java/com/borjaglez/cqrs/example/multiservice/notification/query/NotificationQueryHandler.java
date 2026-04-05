package com.borjaglez.cqrs.example.multiservice.notification.query;

import java.util.List;

import com.borjaglez.cqrs.example.multiservice.notification.domain.Notification;
import com.borjaglez.cqrs.example.multiservice.notification.domain.NotificationRepository;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class NotificationQueryHandler {

  private final NotificationRepository repository;

  public NotificationQueryHandler(NotificationRepository repository) {
    this.repository = repository;
  }

  @HandleQuery
  public List<Notification> handle(GetAllNotificationsQuery query) {
    return repository.findAll();
  }
}
