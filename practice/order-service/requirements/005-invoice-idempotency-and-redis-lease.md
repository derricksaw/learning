# 005 — Create an invoice once and coordinate rendering

**Status:** ready

**Learning:** [Durable idempotency versus temporary ownership](../../../software-design/distributed-concurrency.md#durable-idempotency-versus-temporary-ownership)

**Dependencies:** existing order creation and PostgreSQL. Redis 7.4 is supplied in Compose on local port 6380; both application instances must share the same database and Redis. The Spring Data Redis starter and configuration are supplied.

## Business requirement

An order has **one durable invoice**. Repeated or concurrent invoice-creation requests must return that same invoice, even after an instance restarts.

The invoice can have a slow-generated document. While one instance renders that document, another instance should return `409 / DOCUMENT_BUSY`. Once published, later requests return the same stored document. The Redis key is temporary ownership, not a record that invoicing happened.

This distinction is the exercise: **PostgreSQL remembers completion; Redis reduces duplicate work while it is running.** No Java lock can coordinate independent JVMs.

The document renderer is a labelled text simulator, not a PDF generator or billing integration. Demonstration pricing is MYR 10 per ordered unit. No tax calculation, legal invoicing, payments, or external side effects are implemented.

## Supplied API and storage

| Endpoint | Behavior after completing the gaps |
| --- | --- |
| `POST /orders/{orderId}/invoice` | Create once or return the stored invoice, always `200` |
| `GET /orders/{orderId}/invoice` | Return stored invoice or `404` |
| `POST /orders/{orderId}/invoice/document` | Return an existing document or render/publish it |
| Same missing document being generated | `409 / DOCUMENT_BUSY` |
| Redis unavailable when a new lease is needed | `503 / COORDINATION_UNAVAILABLE`; no unlocked rendering fallback |

No request body is needed. Invoice results contain `id`, `orderId`, `totalMinor`, and `currency`; document results contain `invoiceId`, `documentId`, and labelled demo `content`. Missing orders are `404`; a document request before invoice creation is also `404`. Valid unfinished paths explicitly return `501 / EXERCISE_INCOMPLETE`.

V5 supplies `order_invoice` with unique `order_id`, and `invoice_document` with primary key `invoice_id`. Order IDs are the business identity here because the exercise permits exactly one immutable invoice per order. A generic idempotency header and request-payload matching are outside scope.

Supplied services perform lookup, invoice transaction/readback, rendering, document recheck after lease acquisition, immutable publication, and release via try-with-resources. The short invoice transaction finishes before document rendering. Existing order/inventory behavior is preserved.

## Your three inline TODOs

1. [InvoiceRepository.insertIfAbsent](../src/main/java/dev/learning/orders/invoicing/InvoiceRepository.java): insert a candidate once, returning 1 for insertion or 0 for an existing order invoice. Let PostgreSQL resolve a concurrent unique-key conflict. Never overwrite an invoice on a repeated request. `InvoiceService` reads and returns the stored winner afterward.
2. [RedisDocumentLease.acquire](../src/main/java/dev/learning/orders/invoicing/RedisDocumentLease.java): set the lock key only if absent, storing the supplied token and TTL in the same atomic command. Return whether acquisition succeeded.
3. [RedisDocumentLease.release](../src/main/java/dev/learning/orders/invoicing/RedisDocumentLease.java): atomically compare ownership and delete only on a match, using Lua. Missing or differently owned keys must remain untouched. Do not use separate read/delete commands.

Complete them in that order. An implemented acquisition with an unfinished release still throws on cleanup; the exercise is not complete until all three are reviewed. Leave the provided durable document publication guard intact.

## Framework syntax you need

- `JdbcTemplate.update(...)` binds SQL parameters in `?` order and returns the affected-row count. The unique `order_id` constraint—not a prior `SELECT`—decides who inserted the invoice.
- `StringRedisTemplate` is injected by Spring and serializes keys/values as strings. Its `opsForValue()` exposes string-value operations. `setIfAbsent(key, value, Duration)` combines conditional creation with expiry; inspect its Boolean result rather than assuming success.
- `DefaultRedisScript<Long>` can hold Lua source and the numeric result type. `redis.execute(script, List.of(key), token)` supplies the script key and argument. In Lua, the key is `KEYS[1]` and the token is `ARGV[1]`; use Redis calls inside one script to make the ownership check and deletion atomic. Do not issue those commands separately in Java.
- The supplied `Lease` implements `AutoCloseable`, so service try-with-resources attempts release on success and failure. Each attempt gets a new UUID owner token; the token identifies ownership, not the invoice.

[Spring Redis scripting](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html), [Redis locking protocol](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/), [PostgreSQL conflict handling](https://www.postgresql.org/docs/17/sql-insert.html)

## Review criteria

Code review is the default; no new automated tests are required. Trace these cases against the saved implementation:

| Case | Required behavior |
| --- | --- |
| Two invoice-creation requests for one order | One invoice row; both return its ID and amount |
| Retry after losing the creation response | Stored invoice returned, not replaced |
| Invoice creation with Redis unavailable | Still works; creation does not use Redis |
| Two instances render one missing document under a valid lease | One renders; the other reports busy |
| Requests render documents for different invoices | Different keys permit concurrent work |
| Retry after document completion | Same stored document returned before needing Redis |
| A's lease expires; B acquires the key; A releases late | B's key survives |
| Rendering fails or is interrupted | Release attempted; no successful document response invented |
| Renderer outlives the TTL | Duplicate rendering is possible; one immutable publication remains |
| Redis acquisition fails | No uncoordinated rendering; caller receives unavailable |

Two distinct Redis instances would not coordinate. Similarly, separate PostgreSQL databases would not share invoice identity. Inspect key scope, expiry, argument order, uniqueness, and release atomicity—not merely the happy-path response.

## Optional manual walkthrough

From `practice/order-service/`, set your development `DB_PASSWORD` as described in the README, then:

```bash
docker compose up -d --wait
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Start a second instance in another terminal with the same environment:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  '-Dspring-boot.run.arguments=--server.port=8081'
```

Create an order with the existing endpoint and copy its UUID. After solving the invoice-insert gap, call these repeatedly and through both ports; the invoice ID must stay the same:

```bash
curl -i -X POST http://127.0.0.1:8080/orders/ORDER_UUID/invoice
curl -i -X POST http://127.0.0.1:8081/orders/ORDER_UUID/invoice
```

After solving both Redis gaps, request the document on port 8080. During the default 5-second render, request the same document on port 8081:

```bash
curl -i -X POST http://127.0.0.1:8080/orders/ORDER_UUID/invoice/document
```

```bash
curl -i -X POST http://127.0.0.1:8081/orders/ORDER_UUID/invoice/document
```

The overlapping request should report busy. After completion, both return the same `documentId`. For another missing-document demonstration, create another order/invoice; an existing document deliberately bypasses locking. Repeated invoice/document calls do not consume stock again.

Settings: `invoice.document.lease-millis=30000`, `invoice.document.demo-delay-millis=5000`, `REDIS_HOST=127.0.0.1`, and `REDIS_PORT=6380`. Keep the normal render shorter than the lease. Deliberately increasing render time past the lease illustrates why expiry cannot guarantee exclusive execution.

## Production limits and current verification

The Redis deployment is a single local server without renewal or fencing. The unique owner token protects release, not publication. Lease expiry or failover can produce duplicate renders; that is accepted here. Immutable PostgreSQL publication prevents one renderer replacing another's document. There is no paid external operation to repeat.

If release fails after a document was saved, the request can return unavailable even though publication succeeded. A retry reads the durable document. If a process crashes before publication, lease expiry allows another attempt. No persistent “in progress” invoice record is stranded: the invoice is inserted and committed as one database effect.

A production legal-invoice workflow needs authorization, durable pricing/tax snapshots, auditing, appropriate document storage, and operation/outcome handling for external billing. A strict mutable-resource lock additionally needs resource-side fencing or another guarded write; do not treat this lease as that guarantee.

Scaffold compilation and packaging passed with tests skipped. The TODO paths are intentionally incomplete; Redis interaction and a two-instance walkthrough have not been executed. No new automated exercise tests were added. Request code review after implementing the three TODOs.
