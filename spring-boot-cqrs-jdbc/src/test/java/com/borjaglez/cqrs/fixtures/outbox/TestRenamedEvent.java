package com.borjaglez.cqrs.fixtures.outbox;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.NoArgsConstructor;

@NoArgsConstructor
@CqrsMessage(service = "shop", module = "order", name = "order-shipped")
public class TestRenamedEvent extends Event {}
