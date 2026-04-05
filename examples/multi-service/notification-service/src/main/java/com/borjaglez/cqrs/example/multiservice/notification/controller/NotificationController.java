package com.borjaglez.cqrs.example.multiservice.notification.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.example.multiservice.notification.domain.Notification;
import com.borjaglez.cqrs.example.multiservice.notification.query.GetAllNotificationsQuery;
import com.borjaglez.cqrs.query.QueryBus;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final QueryBus queryBus;

  public NotificationController(QueryBus queryBus) {
    this.queryBus = queryBus;
  }

  @GetMapping
  public ResponseEntity<List<Notification>> getAll() {
    List<Notification> notifications = queryBus.ask(new GetAllNotificationsQuery());
    return ResponseEntity.ok(notifications);
  }
}
