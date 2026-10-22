package com.borjaglez.cqrs.transaction;

/** How {@link TransactionMiddleware} runs queries. */
public enum QueryTransactions {
  /** In a read-only transaction. */
  READ_ONLY,
  /** In a regular read-write transaction. */
  READ_WRITE,
  /**
   * The middleware opens no transaction: the query is passed on as is (it still takes part in a
   * transaction already in progress).
   */
  NONE
}
