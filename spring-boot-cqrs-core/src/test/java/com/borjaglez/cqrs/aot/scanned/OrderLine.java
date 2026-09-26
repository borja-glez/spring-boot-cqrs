package com.borjaglez.cqrs.aot.scanned;

/** A record nested in a message: Jackson needs its components in a native image. */
public record OrderLine(String sku, int quantity) {}
