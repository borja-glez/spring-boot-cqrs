package com.borjaglez.cqrs.fixtures.outbox;

import com.borjaglez.cqrs.naming.CqrsMessage;

@CqrsMessage(service = "shop", module = "order", name = "not-an-event")
public class TestAnnotatedNotAnEvent {}
