# Nirvaankar — Marketplace Backend

Multi-vendor marketplace for handmade, eco-friendly and natural products made by Indian artisans.

Java 17 · Spring Boot 3.5 · MySQL 8 · Flyway · Redis · Modular monolith, microservice-ready.

---

## Quick start

```bash
# 1. Infrastructure
docker compose up -d          # MySQL 8 + Redis

# 2. Generate the JWT signing keypair (RS256, never commit these)
mkdir -p .secrets
openssl genpkey -algorithm RSA -out .secrets/jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in .secrets/jwt-private.pem -out .secrets/jwt-public.pem

export NIRVAANKAR_JWT_PRIVATE_KEY="$(cat .secrets/jwt-private.pem)"
export NIRVAANKAR_JWT_PUBLIC_KEY="$(cat .secrets/jwt-public.pem)"

# 3. Run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Tests need Docker (Testcontainers spins up a real MySQL):

```bash
./mvnw test
```

---

## What is built

| Phase | Scope | Status |
|---|---|---|
| 0 | Project skeleton, config, Docker, Flyway, Testcontainers | done |
| 1 | `common` foundation + full **identity** module + idempotency | done |
| 1 | **Complete database schema — all 14 modules, every table** | done |
| 2 | Seller onboarding + catalog service code | schema ready, Java pending |
| 3 | Inventory, cart, orders service code | schema ready, Java pending |
| 4 | Payments, ledger, payouts service code | schema ready, Java pending |
| 5 | Fulfilment, promotions, reviews service code | schema ready, Java pending |
| 6 | SDUI, flags, outbox workers service code | schema ready, Java pending |

Every table from the architecture document exists and is verified by
`SchemaMigrationTest`. The remaining phases add Java on top of a schema that is
already correct — which is the expensive half to get wrong.

---

## Architecture in one page

**Modular monolith.** One deployable, one database, but each module owns its
package and talks to others only through service interfaces. No JPA
relationship crosses a module boundary; `OrderItem` holds a `sellerId` as a
`Long`, not a `@ManyToOne Seller`. `ModuleBoundaryTest` (ArchUnit) fails the
build if that slips. This is the one discipline that decides whether the future
split into services is a two-week job or a six-month rewrite.

**Money is `BIGINT` minor units.** `149900` = ₹1499.00. Every money column ends
in `_minor` and carries a `currency`. No `double`, no `float`, and `BigDecimal`
only at the tax-rate multiplication boundary with an explicit rounding mode.

**Time is UTC.** `DATETIME(6)` in MySQL, `Instant` in Java, connection pinned to
UTC. Conversion happens in the client.

**Internal `BIGINT` id, external UUID v7.** Public ids are time-ordered UUIDs
stored as `BINARY(16)`. Internal ids never appear in a URL or a response body.

**Append-only for money and stock.** `ledger_entries` and
`inventory_transactions` are never updated or deleted. Corrections are reversing
entries. That is the entire basis of reconciliation.

**Snapshot at transaction time.** Order lines copy the product name, SKU, price
and commission rate. The master can change; order history cannot.

---

## The N+1 policy

An N+1 is treated as a bug of the same severity as a wrong price.

- `spring.jpa.open-in-view: false` — not negotiable.
- Every `@ManyToOne` and `@OneToOne` is explicitly `LAZY`; `PersistenceConventionTest`
  fails the build on any `EAGER`.
- List endpoints return **DTO projections built by one query**, never entity graphs.
- `JOIN FETCH` with pagination is forbidden — Hibernate would page in memory.
  Use the two-query pattern: page the ids, then fetch by `id IN (:ids)`.
- Aggregates are pre-computed (`product_rating_summary`, `ledger_accounts.balance_minor`),
  never `AVG()`/`SUM()` on the hot path.
- Every list endpoint has a test asserting its query count does not grow with
  row count. See `AddressQueryCountTest`.

---

## Concurrency

Java 17, so platform threads only — no virtual threads.

- Every executor is a named, bounded `ThreadPoolTaskExecutor` in `AsyncConfig`.
  A bare `@Async` is a review rejection; the executor is always named.
- `CallerRunsPolicy` on every pool, so a spike applies backpressure instead of
  filling an unbounded queue and running the JVM out of memory.
- `ContextPropagatingTaskDecorator` carries the trace id and the security
  context across the thread boundary, without which audit logs lose the actor.
- Async work is triggered **after commit**, never inside the transaction.
- Scheduled jobs are wrapped in ShedLock so two instances don't both run them.
- Queue-style workers drain with `FOR UPDATE SKIP LOCKED`.
- Locking, per table: inventory uses a conditional atomic `UPDATE` (the database
  does the mutual exclusion), order status uses optimistic `@Version`, ledger
  posting uses `SELECT ... FOR UPDATE`, coupon counters use a conditional
  increment. No `synchronized` anywhere — a JVM lock protects nothing across
  multiple instances.

The thread budget and the Hikari pool size are reconciled in a comment at the
top of `application.yml`. Adding an executor means updating both.

---

## Postgres → MySQL

The architecture document was written for PostgreSQL 16. Table and column names
are unchanged; the physical types are translated:

| Document | Here |
|---|---|
| `BIGSERIAL` | `BIGINT UNSIGNED AUTO_INCREMENT` |
| `UUID` (v7) | `BINARY(16)`, generated time-ordered in Java |
| `TIMESTAMPTZ` | `DATETIME(6)` + UTC-pinned connection |
| `JSONB` | `JSON` |
| `CITEXT` | `VARCHAR` + `utf8mb4_0900_ai_ci` |
| `LTREE` | materialized path `VARCHAR`, `LIKE '1/14/%'` for subtrees |
| `TSVECTOR` + GIN | `FULLTEXT` index (until OpenSearch in phase 6) |
| `INET` | `VARBINARY(16)` via `INET6_ATON()` |
| `BYTEA` | `VARBINARY` + AES-GCM in the app layer |
| partial unique index | STORED generated column that goes `NULL` on soft delete |
| Postgres schemas | Java packages + ArchUnit boundary tests |

Two MySQL constraints shaped the schema and are worth remembering:

1. A partitioned table cannot take part in a foreign key in either direction.
   `orders` is partitioned, so `order_items` has no FK to it and the ordering
   service is the sole guarantor of that reference.
2. The partition column must appear in every unique key, which is why `orders`,
   `audit_logs`, `ledger_entries` and `search_queries` have composite primary
   keys ending in a timestamp.

---

## Method naming

| Layer | Pattern |
|---|---|
| Repository, single | `findBy<Field>` → `Optional<T>` |
| Repository, list | `findAllBy<Criteria>` |
| Repository, projection | `find<Projection>By<Criteria>` |
| Service, query | `find*` / `get*` (throws) / `list*` |
| Service, command | `placeOrder`, `reserveInventory`, `approveSellerPayout` |
| Service, validation | `ensure*` / `validate*` (void, throws) |
| Service, calculation | `calculate*` (pure, no I/O) |
| Scheduled | `scheduled<Task>` |
| Boolean | `is*` / `has*` / `can*` |

No `*Manager`, no `*Helper`, no `process`/`handle`/`doStuff`. Methods cap at 40
lines; past that, extract and let the extracted name be the documentation.

---

## The three panels

One `users` table, no `role` column — roles come from `user_roles`, so a person
can be a customer and a seller at once.

- `/api/v1/**` — customer
- `/api/v1/seller/**` — seller dashboard
- `/api/v1/admin/**` — admin console

Separate `SecurityFilterChain` per prefix. Authorization is by permission
(`@PreAuthorize("hasAuthority('payout.approve')")`), never by comparing a role
string. Seller scoping is enforced in the service layer: every seller query
takes the authenticated `sellerId` and applies it as a `WHERE` clause, so one
seller is structurally unable to read another's orders.

Access tokens are 15-minute RS256 JWTs. Refresh tokens are opaque, stored
hashed, bound to a device, and rotated on every use with a `replaced_by` chain —
presenting an already-rotated token is treated as theft and revokes the chain.

---

## Layout

```
src/main/java/com/nirvaankar/marketplace/
├── common/        config, errors, money, ids, pagination, security, idempotency
├── identity/      users, roles, devices, tokens, addresses          [built]
├── seller/  catalog/  inventory/  cart/  promotion/  ordering/      [next]
├── payment/  fulfilment/  ledger/  review/  sdui/  flags/  platform/
src/main/resources/db/migration/                                    [complete]
```

---

## Before production

- [ ] Move the JWT keypair and the field-encryption key into a real KMS.
- [ ] Set `nirvaankar.otp.expose-in-response: false` (dev/test only).
- [ ] Restore `bcrypt-strength: 12` — the test profile lowers it for speed.
- [ ] Schedule the monthly partition job; if it stops, inserts past the last
      partition fail loudly, which is the intended behaviour.
- [ ] Point `spring.datasource` at a read replica for catalog and listing traffic.
- [ ] Run `EXPLAIN ANALYZE` on every query touching `orders`, `order_items`,
      `products`, `inventory_levels` and `ledger_entries`.
"# nirvaankar-backend" 
