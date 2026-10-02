CREATE TABLE IF NOT EXISTS cqrs_processed_message (
  handler_id   VARCHAR(255) NOT NULL,
  message_id   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMP    NOT NULL,
  PRIMARY KEY (handler_id, message_id)
);
CREATE INDEX IF NOT EXISTS cqrs_processed_message_at ON cqrs_processed_message (processed_at);
