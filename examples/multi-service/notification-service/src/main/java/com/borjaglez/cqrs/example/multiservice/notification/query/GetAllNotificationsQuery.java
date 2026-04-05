package com.borjaglez.cqrs.example.multiservice.notification.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

@CqrsMessage(
    service = "notification-service",
    module = "notification",
    name = "get-all-notifications")
public class GetAllNotificationsQuery extends Query {}
