CREATE TABLE stock_ledger (
  sku            VARCHAR(64) PRIMARY KEY,
  available_qty  INT    NOT NULL CHECK (available_qty >= 0),
  reserved_qty   INT    NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
  version        BIGINT NOT NULL DEFAULT 0,          -- optimistic lock
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations (
  id          BIGSERIAL PRIMARY KEY,
  order_id    UUID        NOT NULL,
  sku         VARCHAR(64) NOT NULL REFERENCES stock_ledger(sku),
  quantity    INT         NOT NULL CHECK (quantity > 0),
  status      VARCHAR(16) NOT NULL,                  -- RESERVED, COMMITTED, RELEASED, EXPIRED
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (order_id, sku)                             -- safe against re-delivery
);
CREATE INDEX idx_reservations_order  ON stock_reservations (order_id);
CREATE INDEX idx_reservations_expiry ON stock_reservations (expires_at) WHERE status = 'RESERVED';

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
