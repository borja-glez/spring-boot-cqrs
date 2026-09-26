package com.borjaglez.cqrs.aot.scanned;

import com.borjaglez.cqrs.naming.CqrsMessage;

/** A message type recognised only by its annotation. */
@CqrsMessage(service = "test", module = "aot", name = "tagged")
public record Tagged(String value) {}
