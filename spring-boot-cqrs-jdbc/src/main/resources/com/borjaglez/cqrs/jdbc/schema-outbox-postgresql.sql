CREATE TABLE IF NOT EXISTS ${table} (
  id           BIGSERIAL     PRIMARY KEY,
  event_id     VARCHAR(64)   NOT NULL UNIQUE,
  event_name   VARCHAR(255)  NOT NULL,
  event_class  VARCHAR(512)  NOT NULL,
  payload      BYTEA         NOT NULL,
  context      BYTEA,
  created_at   TIMESTAMP     NOT NULL,
  published_at TIMESTAMP,
  failed_at    TIMESTAMP,
  attempts     INT           NOT NULL DEFAULT 0,
  last_error   VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS ${index}_pending ON ${table} (id) WHERE published_at IS NULL AND failed_at IS NULL;
CREATE INDEX IF NOT EXISTS ${index}_published ON ${table} (published_at) WHERE published_at IS NOT NULL;
