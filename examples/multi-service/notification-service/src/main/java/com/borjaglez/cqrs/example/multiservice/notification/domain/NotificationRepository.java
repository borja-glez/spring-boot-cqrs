package com.borjaglez.cqrs.example.multiservice.notification.domain;

import java.util.List;

public interface NotificationRepository {

  void save(Notification notification);

  List<Notification> findAll();
}
