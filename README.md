# E-Commerce Transactional Core (Spring Boot 3.3 / Java 17)

Order placement → real-time inventory reservation → payment, coordinated by an **orchestrated Saga**
(Order Service is the orchestrator) over **Kafka**, with a **transactional outbox** in every service.

```
Client → API Gateway (JWT, rate limit, CB) ─lb://→ order-service ─┐
                                            inventory-service ◄───┤ Kafka (order/inventory/payment .events|.commands)
                                            payment-service   ◄───┘
Config Server (8888) · Eureka (8761) · Postgres ×3 · Redis (rate limiter)
```

| Module | Port | Notes |
|---|---|---|
| config-server | 8888 | native profile, serves `src/main/resources/config-repo` (switch to Git for prod) |
| discovery-server | 8761 | Eureka, standalone |
| api-gateway | 8080 | OAuth2 resource server, Redis rate limiter, per-route circuit breaker |
| order-service | 8081 | checkout API, saga orchestrator, timeout compensation |
| inventory-service | 8082 | stock ledger, reservations (optimistic lock or atomic UPDATE), expiry sweeper |
| payment-service | 8083 | idempotent PSP charge, Resilience4j, reconciliation, **built-in mock PSP** |
| common-libs/common-events | – | event records + topic names |
| common-libs/common-outbox | – | outbox entity/relay, idempotent-consumer guard, DLT error handler, topics |

## Saga flow
`POST /checkout` → order `PENDING` + `OrderCreated` (outbox) → inventory reserves all lines atomically →
`InventoryReserved` → order `PAYMENT_PENDING` + `PaymentRequested` → payment charges PSP (idempotency key) →
`PaymentSucceeded` → order `CONFIRMED` + `CommitReservation`; or `PaymentFailed` → `CANCELLED` + `ReleaseReservation`;
or `InventoryRejected` → `REJECTED`. Timeouts in `PENDING` / `PAYMENT_PENDING` compensate automatically.

## Run it
Requirements: JDK 17+, Maven 3.9+, Docker.

**Option A – everything in Docker**
```bash
docker compose --profile apps up -d --build
```

**Option B – infra in Docker, services from your IDE/terminal** (start in this order)
```bash
docker compose up -d                       # 3× Postgres, Kafka (KRaft), Redis
mvn clean install -DskipTests
mvn -pl config-server spring-boot:run &
mvn -pl discovery-server spring-boot:run &
mvn -pl order-service spring-boot:run &
mvn -pl inventory-service spring-boot:run &
mvn -pl payment-service spring-boot:run &
mvn -pl api-gateway spring-boot:run &
```
(The config server is optional: every service has working defaults and starts without it.)

**Try it**
```bash
scripts/demo.sh                 # happy path
scripts/demo.sh tok_decline     # payment declined -> order CANCELLED -> stock released
scripts/demo.sh tok_timeout     # PSP is slow on 1st call; retry with same Idempotency-Key -> exactly one charge
```
Seed data: `SKU-IPHONE-15-128` (100), `SKU-CASE-BLK` (500), `SKU-LIMITED-1` (3 → order 4 to see `REJECTED`).
Manual calls need a token: `python3 scripts/dev_token.py` (dev-only HS256 secret shared with the gateway).

## Configuration you should change for production
- **Auth:** remove `gateway.security.jwt-secret`, set `spring.security.oauth2.resourceserver.jwt.{jwk-set-uri,issuer-uri,audiences}`.
- **Secrets/DB creds:** currently `shop/shop`; inject via env/Vault/K8s secrets.
- **Kafka:** `outbox.topic-replicas=3`, `min.insync.replicas=2`; consider Debezium instead of the polling relay at high volume.
- **PSP:** set `psp.mock.enabled=false` and point `psp.base-url` at the real provider adapter.
- **Config server:** switch `native` → `git`, and secure it.
- **Eureka:** run ≥2 peers.
- `inventory.reservation.ttl` must stay **longer** than `saga.payment-timeout`.

## Known limitations (deliberately out of scope)
- Refund flow: if a payment succeeds for an order that was already cancelled, the order service logs an `ALERT`; a refund saga is not implemented.
- No cart/catalog/shipping services; prices come from a seeded `product_prices` table in the order DB.
- Reconciliation retries forever (no max-age / manual-review queue yet).
- No automated integration tests (Testcontainers) yet; only a `StockLedger` unit test is included.
- The code was syntax-checked but **not compiled or run** in the authoring environment (no Maven Central access) — run `mvn clean verify` first and treat any compile error as a bug to report back.
