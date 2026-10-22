package com.borjaglez.cqrs.fixtures.scan.inheritance.detailed;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.fixtures.scan.inheritance.base.AuditEventHandler;

@EventHandler
public class DetailedAuditEventHandler extends AuditEventHandler {}
