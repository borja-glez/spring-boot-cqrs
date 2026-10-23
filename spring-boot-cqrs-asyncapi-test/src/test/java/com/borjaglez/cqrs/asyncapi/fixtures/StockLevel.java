package com.borjaglez.cqrs.asyncapi.fixtures;

/** Reply of {@link CheckStock}. */
public record StockLevel(String sku, int available) {}
