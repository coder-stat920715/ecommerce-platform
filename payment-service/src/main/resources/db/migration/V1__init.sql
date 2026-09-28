CREATE TABLE payment_intents (
  id                   UUID PRIMARY KEY,
  order_id             UUID          NOT NULL UNIQUE,      -- one intent per order, even on Kafka redelivery
  amount               NUMERIC(12,2) NOT NULL,
  currency             CHAR(3)       NOT NULL,
  status               VARCHAR(24)   NOT NULL,             -- PROCESSING, SUCCEEDED, FAILED (REFUNDED reserved)
  idempotency_key      VARCHAR(64)   NOT NULL UNIQUE,      -- "pay-<orderId>", sent to the PSP
  payment_method_token VARCHAR(128)  NOT NULL,
  psp_reference        VARCHAR(128),
  failure_code         VARCHAR(64),
  version              BIGINT        NOT NULL DEFAULT 0,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_intents_status_updated ON payment_intents (status, updated_at);

CREATE TABLE payment_transactions (
  id            BIGSERIAL PRIMARY KEY,
  intent_id     UUID          NOT NULL REFERENCES payment_intents(id),
  type          VARCHAR(16)   NOT NULL,                    -- CHARGE, REFUND
  status        VARCHAR(16)   NOT NULL,                    -- SUCCEEDED, DECLINED, DEFERRED, UNKNOWN
  amount        NUMERIC(12,2) NOT NULL,
  psp_response  JSONB,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_tx_intent ON payment_transactions (intent_id);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64)  NOT NULL,
  aggregate_id   VARCHAR(64)  NOT NULL,
  topic          VARCHAR(128) NOT NULL,
  event_type     VARCHAR(64)  NOT NULL,
  payload        JSONB        NOT NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';

CREATE TABLE processed_events (
  event_id     UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
