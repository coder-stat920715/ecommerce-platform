# Testing Guide (step by step)

Covers: startup → Postman setup → running every folder and what to expect → database/Kafka verification →
concurrency test → failure drills → troubleshooting.

> The project has been syntax-checked but not compiled/run by its author. If a step fails, check
> "Troubleshooting" at the bottom, then report the exact error.

---
## 1. Prerequisites
| Tool | Version |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| Docker + Compose v2 | any recent |
| Postman | v10+ (desktop app; the web version cannot reach `localhost` without the agent) |
| Python 3 | for `scripts/*` |

## 2. Start the system
**Option A: everything in Docker**
```bash
docker compose --profile apps up -d --build      # first build takes several minutes
docker compose ps                                # wait until all containers are Up
```
**Option B: infrastructure in Docker, apps locally** (open one terminal per service, in this order)
```bash
docker compose up -d                             # 3x Postgres, Kafka, Redis
mvn clean install -DskipTests
mvn -pl config-server    spring-boot:run         # optional
mvn -pl discovery-server spring-boot:run
mvn -pl order-service    spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl payment-service  spring-boot:run
mvn -pl api-gateway      spring-boot:run
```
**Ready check** (allow ~30-60 s after the last service starts so Eureka propagates registrations):
1. Open http://localhost:8761: you should see API-GATEWAY, ORDER-SERVICE, INVENTORY-SERVICE, PAYMENT-SERVICE.
2. `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

## 3. Import into Postman
1. Postman → **Import** → select both files from `postman/`:
   - `Shop-Platform.postman_collection.json`
   - `Shop-Local.postman_environment.json`
2. Top-right environment dropdown → choose **Shop - Local**.
3. Environment variables (edit only if you changed ports/secret):

| Variable | Default | Meaning |
|---|---|---|
| baseUrl | http://localhost:8080 | API gateway |
| orderUrl / inventoryUrl / paymentUrl | :8081 / :8082 / :8083 | direct service ports (debugging) |
| eurekaUrl | http://localhost:8761 | service registry |
| jwtSecret | dev-only-shared-secret-... | must equal `gateway.security.jwt-secret` |
| userId / otherUserId | 1111... / 2222... | UUIDs used as JWT `sub` |

**How auth works in the collection:** a collection-level *pre-request script* mints HS256 JWTs from `jwtSecret`
(`token`, `tokenOther`, `tokenExpired`, `tokenForged`), and the collection uses `Bearer {{token}}`. You never paste a token.

**How to run it:**
- Single request: click **Send**. Works everywhere, except the "Poll" requests do not auto-repeat (click Send again until the status is terminal).
- Whole folder or collection: right-click → **Run** (Collection Runner). Keep *Delay* at ~300 ms, *Persist variables* on. Poll requests auto-repeat every 1.5 s (max 20 tries).
- Run folders **in order 00 → 07**. Uncheck **08 (manual drills)** and, for the first pass, **06 (rate limiting)**.

## 4. Folder-by-folder walkthrough

### 00 Smoke & Infrastructure
| Request | Expected |
|---|---|
| 0.1 Gateway health | 200, `UP` |
| 0.2 Eureka registry | 200; all four services listed |
| 0.3-0.5 Direct health | 200, `UP` |

If 0.2 lists fewer than four services, wait 30 s and retry (Eureka registration lag).

### 01 Gateway authentication
| Request | Expected | What it proves |
|---|---|---|
| 1.1 No token | **401** | gateway is a resource server |
| 1.2 Garbage token | **401** | malformed JWT rejected |
| 1.3 Expired token | **401** | `exp` validated |
| 1.4 Wrong-secret token | **401** | signature validated |
| 1.5 Valid token | **200** + stock JSON | happy auth path, routing via Eureka `lb://` |

### 02 Happy path (the core saga)
Run 2.1 → 2.7 in order.

| Step | Request | Expected | Behind the scenes |
|---|---|---|---|
| 2.1 | Stock BEFORE | 200; values saved | records available/reserved of the iPhone |
| 2.2 | Checkout (1 iPhone + 2 cases) | **202**, `status=PENDING`, `totalAmount=84999` | order row + `OrderCreated` outbox row in one TX; price recomputed server-side (79999 + 2x2500) |
| 2.3 | Replay same Idempotency-Key | **200**, same `orderId` | UNIQUE(customer_id, idempotency_key) → no duplicate order |
| 2.4 | Same key, different body | **422** problem+json | request-hash mismatch detected |
| 2.5 | Poll order | ends **CONFIRMED** | PENDING → PAYMENT_PENDING → CONFIRMED |
| 2.6 | Payment status | `SUCCEEDED`, `pspReference=psp_...`, amount 84999 | intent + PSP call with key `pay-<orderId>` |
| 2.7 | Stock AFTER | available = before − 1; reserved = before | reserve moved available→reserved, commit consumed reserved |

### 03 Failure paths & compensation
| Step | Expected | Explanation |
|---|---|---|
| 3.1-3.2 | 202 for a `tok_decline` order | failure only appears asynchronously |
| 3.3 | Poll → **CANCELLED**, `failureReason=PSP_DECLINED` | PSP returned 402 → `PaymentFailed` |
| 3.4 | Case stock **exactly restored** | orchestrator sent `ReleaseReservation` (compensation) |
| 3.5-3.6 | 4 × `SKU-LIMITED-1` → **REJECTED**, reason contains `SKU-LIMITED-1` | all-or-nothing reservation failed, `InventoryRejected` |
| 3.7-3.9 | `tok_timeout` → **CONFIRMED**, one PSP reference | first PSP call stalls 6 s, client times out at 3 s, Resilience4j retries with the same key, the PSP replays the original result: **no double charge** |

3.7-3.8 take about 5-8 seconds. That is expected.

### 04 Validation & business rules
| Request | Expected |
|---|---|
| 4.1 Empty items | 400 |
| 4.2 Quantity 0 | 400 |
| 4.3 Missing `Idempotency-Key` | 400 |
| 4.4 Unknown SKU | 422 (detail names the SKU) |
| 4.5 Currency mismatch | 422 |
| 4.6 Unknown inventory SKU | 404 |

### 05 Identity propagation & ownership
| Request | Expected | Proves |
|---|---|---|
| 5.1 Other user reads my order | **404** | ownership enforced, existence hidden |
| 5.2 Owner reads own order | 200 | |
| 5.3 Checkout with spoofed `X-User-Id` | 202 | |
| 5.4 Real token owner reads it | 200 | gateway stripped the spoofed header; JWT `sub` wins |
| 5.5 Spoofed user reads it | 404 | |

### 06 Rate limiting (run separately)
1. Right-click folder **06** → Run. Select only request 6.1, **Iterations: 20, Delay: 0 ms**.
2. Expected: the first ~10 return **202** (burst capacity), later ones **429** with `X-RateLimit-Remaining: 0`.
   Exact numbers vary (5 tokens/s refill). The collection variable `throttled` counts the 429s and should be **> 0**.
3. Wait 3 s for the bucket to refill; set `throttled` back to 0.

### 07 Idempotency & resilience (direct)
| Request | Expected |
|---|---|
| 7.1 / 7.2 Mock PSP, same key twice | identical `pspReference` (the PSP-side half of the double-charge protection) |
| 7.3 Circuit breakers | `pspClient` exists, state `CLOSED` |
| 7.4 Direct inventory call | 200 without JWT. Internal ports must not be exposed publicly in production |

## 5. Verify the internals (database and Kafka)
Use the order id printed in Postman (`orderId` collection variable).

**Order + saga rows**
```bash
docker compose exec postgres-order psql -U shop -d orderdb -c \
 "select id,status,total_amount,failure_reason from orders order by created_at desc limit 5;"
docker compose exec postgres-order psql -U shop -d orderdb -c \
 "select event_type,topic,status from outbox_events order by created_at desc limit 10;"
```
Expected outbox event types for a happy order: `OrderCreated`, `PaymentRequested`, `CommitReservation`, all `PUBLISHED`.

**Inventory**
```bash
docker compose exec postgres-inventory psql -U shop -d inventorydb -c "select * from stock_ledger;"
docker compose exec postgres-inventory psql -U shop -d inventorydb -c \
 "select order_id,sku,quantity,status from stock_reservations order by created_at desc limit 10;"
```
Reservation status flow: `RESERVED → COMMITTED` (paid) or `RESERVED → RELEASED` (declined/timeout).

**Payment**
```bash
docker compose exec postgres-payment psql -U shop -d paymentdb -c \
 "select order_id,status,idempotency_key,psp_reference,failure_code from payment_intents order by created_at desc limit 5;"
docker compose exec postgres-payment psql -U shop -d paymentdb -c \
 "select intent_id,type,status,amount from payment_transactions order by id desc limit 10;"
```
For the `tok_timeout` order there must be **exactly one** intent and its `idempotency_key` = `pay-<orderId>`.

**Kafka**
```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
  --topic payment.events --from-beginning --property print.key=true --property print.headers=true
```
Key = order id (same partition → ordered per order); headers show `event-id` and `event-type`.
Any `*.DLT` topic appearing means a message exhausted its retries.

**Idempotent consumers**: `select count(*) from processed_events;` in each DB grows by one per handled event and never twice for the same event id.

## 6. Concurrency / oversell test
Proves optimistic locking + all-or-nothing reservation under load.
```bash
scripts/concurrency_test.sh 10
```
Expected: `CONFIRMED=3 REJECTED=7 OTHER=0`, final stock `available=0, reserved=0`.
Then reset with the SQL printed at the end of the script (or the stock stays exhausted for later runs).
Also watch the inventory-service log: you may see optimistic-lock retries, which is normal. To compare strategies, set
`inventory.reservation.atomic=true` and re-run.

## 7. Failure drills (resilience)
| Drill | Steps | Expected |
|---|---|---|
| **Kafka down** | `docker compose stop kafka` → send a checkout (2.2) → `docker compose start kafka` | Checkout still returns 202 (outbox row `PENDING`). After Kafka returns, the row turns `PUBLISHED` and the order completes. No lost event. |
| **Payment service down** | `docker compose stop payment-service` (or Ctrl+C) → checkout → wait 20 s → start it again | Order sits in `PAYMENT_PENDING`; after restart the buffered `PaymentRequested` is consumed and the order becomes `CONFIRMED`. |
| **Order service down** | stop order-service, wait ~5-10 s, run **08.1** | **503** with `Retry-After` from the gateway fallback. |
| **Duplicate delivery** | Re-run 2.3 many times | Always 200, one order row, one payment row. |
| **Saga timeout** | Stop inventory-service, checkout, wait > `saga.pending-timeout` (5 min; lower it to `PT30S` in `config-repo/order-service.yml` for testing) | Order → `CANCELLED` (`TIMEOUT_IN_PENDING`) and a release command is queued. |
| **PSP outage** | Set `PSP_BASE_URL` to an unreachable host, send ~15 checkouts | Breaker opens (7.3 → `OPEN`); intents stay `PROCESSING`; after the PSP is back the reconciler completes them and orders confirm. |

## 8. Troubleshooting
| Symptom | Likely cause / fix |
|---|---|
| Gateway returns 503/`Unable to find instance` | Service not yet registered in Eureka: wait 30-60 s; check http://localhost:8761. |
| All requests 401 with a valid-looking token | `jwtSecret` in Postman ≠ `gateway.security.jwt-secret` (or `GATEWAY_JWT_SECRET`). Must be ≥ 32 bytes. |
| 429 on normal use | Rate limiter: checkout allows 5 req/s per user, burst 10. Slow the Runner delay down. |
| Gateway fails at startup / rate-limit errors | Redis not running: `docker compose up -d redis`. |
| Order stuck in `PENDING` | Inventory service down or Kafka unreachable; inspect `outbox_events` (PENDING rows) and `*.DLT` topics. |
| Order stuck in `PAYMENT_PENDING` | Payment service down, or PSP unresolved; reconciler acts after 1 min; check `payment_intents.status`. |
| Services can't reach Kafka from the host | Use `localhost:29092` (default). Inside Docker the address is `kafka:9092`. |
| Flyway "Unsupported Database" | Ensure `flyway-database-postgresql` is on the classpath (it is in each service POM). |
| Postman "Could not get response" | Service not up, or using Postman web without the desktop agent. |
| Poll request doesn't auto-repeat | You used *Send*; use the Collection Runner (or re-send manually). |
| Tests in 2.7/3.4 flaky | Async commit/release hadn't landed yet. Re-send once, or raise the delay in the previous request's test script. |
