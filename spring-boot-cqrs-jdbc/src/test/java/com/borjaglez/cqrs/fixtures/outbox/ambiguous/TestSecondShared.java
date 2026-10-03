package com.borjaglez.cqrs.fixtures.outbox.ambiguous;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

@CqrsMessage(service = "shop", module = "order", name = "shared")
public class TestSecondShared extends Event {}
