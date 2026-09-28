CREATE TABLE orders (
  id                   UUID PRIMARY KEY,
  customer_id          UUID          NOT NULL,
  status               VARCHAR(32)   NOT NULL,   -- PENDING, PAYMENT_PENDING, CONFIRMED, CANCELLED, REJECTED
  total_amount         NUMERIC(12,2) NOT NULL,
  currency             CHAR(3)       NOT NULL,
  idempotency_key      VARCHAR(64)   NOT NULL,
  request_hash         VARCHAR(64)   NOT NULL,   -- detects same key + different body (422)
  shipping_address_id  UUID          NOT NULL,
  payment_method_token VARCHAR(128)  NOT NULL,   -- PSP token, never raw card data
  failure_reason       VARCHAR(255),
  version              BIGINT        NOT NULL DEFAULT 0,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);

CREATE TABLE order_items (
  id          BIGSERIAL PRIMARY KEY,
  order_id    UUID          NOT NULL REFERENCES orders(id),
  sku         VARCHAR(64)   NOT NULL,
  quantity    INT           NOT NULL CHECK (quantity > 0),
  unit_price  NUMERIC(12,2) NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE product_prices (
  sku         VARCHAR(64) PRIMARY KEY,
  unit_price  NUMERIC(12,2) NOT NULL,
  currency    CHAR(3)       NOT NULL
);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64)  NOT NULL,
  aggregate_id   VARCHAR(64)  NOT NULL,          -- Kafka key: keeps one order's events ordered
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
