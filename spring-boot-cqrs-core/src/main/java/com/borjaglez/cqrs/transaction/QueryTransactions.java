package com.borjaglez.cqrs.transaction;

/** How {@link TransactionMiddleware} runs queries. */
public enum QueryTransactions {
  /** In a read-only transaction. */
  READ_ONLY,
  /** In a regular read-write transaction. */
  READ_WRITE,
  /** Without a transaction: the query is passed on as is. */
  NONE
}
