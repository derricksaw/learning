# Order service

A prepared application for focused practice. Codex supplies supporting code and tests; you complete the topic-related gaps and ask for review.

**Stack:** Java 21, Spring Boot 4.1.2, Maven Wrapper, Spring MVC, JDBC, PostgreSQL 17, Flyway, and Lombok.

## Invoice idempotency and Redis ownership

[005 — Create an invoice once and coordinate rendering](requirements/005-invoice-idempotency-and-redis-lease.md) supplies invoice/document endpoints, PostgreSQL storage, and a Redis service in Compose. Complete three inline TODOs: insert an invoice once, acquire an expiring Redis lease, and release only when its owner token matches. The text renderer is a demo, not a PDF or billing integration. Review is the default; unfinished paths return `501`. Redis listens on `127.0.0.1:6380`.

## Distributed concurrency practice

[004 — Reject stale stock corrections across instances](requirements/004-distributed-stock-corrections.md) adds an inventory version and a stock-correction endpoint. `InventoryRepository.replaceIfVersionMatches` passed code review: it checks the expected version and changes stock and version in one write. Exercise 004 is complete by inspection; the optional two-process walkthrough has not been run.

## CAS practice

[003 — Protect carrier calls with local CAS capacity](requirements/003-claim-local-capacity.md) now connects your reviewed CAS component to `POST /orders/{orderId}/shipping-labels`. The supplied carrier simulates slow calls and purchases no postage. `ShippingLabelService.createWithCapacity` has passed code review: it claims a slot before calling the carrier, rejects excess requests with `503`, and releases the claim on success or failure. Runtime verification of this implementation remains pending. The requirement includes unit checks, HTTP checks, and a manual contention walkthrough.

## Current review

[002 — Lock inventory before adjusting stock](requirements/002-lock-inventory-adjustments.md) has passed code review by inspection: `InventoryRepository.findForAdjustment` now locks the row before the supplied service calculates, writes, and records the adjustment. Its completed exercise marker has been removed. Runtime checks remain pending.

[001 — Place orders without overselling](requirements/001-place-orders.md) also has a code-reviewed implementation. Its runtime checks and restart demonstration remain pending.

---

## Run the checks first

From this directory:

```bash
# Prepared endpoints, migrations, validation, and database reads.
./mvnw -Dtest=OrderServiceApplicationTests,ScaffoldTests,AdjustmentSupportTests test

# Verify the locking implementation when ready.
./mvnw -Dtest=LockingExerciseTests test

# Prior transaction exercise, when you want runtime confirmation.
./mvnw -Dtest=TransactionExerciseTests test
```

Tests automatically start a temporary **real PostgreSQL 17.11** process using [embedded-postgres](https://github.com/zonkyio/embedded-postgres). They use random ports, run migrations, and reset fixtures per test. Docker is not required for tests. The first run downloads native binaries; use a regular OS account because PostgreSQL refuses to run as root. Supported platforms depend on the library's native binary availability.

The transaction exercise checks placement and rollback; the locking exercise checks row protection, adjustment audits, and competing operations. Tests are not transaction-wrapped: assertions inspect persisted state after HTTP requests finish. The failure hook is test-only and cannot be armed through the API.

`./mvnw verify` includes all exercises, including the reviewed shipping-label integration in exercise 003. Its result has not been verified after the latest learner changes; code review alone does not establish a test pass.

---

## Run the application locally

Install Java 21+ and Docker Compose, or provide an existing PostgreSQL database. Choose a local password outside Git:

```bash
export DB_PASSWORD='choose-a-local-development-password'
docker compose up -d --wait
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The database listens on `127.0.0.1:5433`; the API listens on `127.0.0.1:8080`. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` configure an existing database instead. Default database/user are both `orders`. Only the `dev` profile seeds product 1 with stock 3, once, through a migration.

```bash
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:8080/inventory/1
curl -i -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}' http://127.0.0.1:8080/orders
```

The new adjustment endpoint accepts `POST /inventory/1/adjustments` with `{"delta":-2,"reason":"damaged stock"}`. Successful adjustments can be read at `GET /inventory/adjustments/{adjustmentId}`.

Health should report `UP`; inventory initially reports 3. Both POST endpoints now have code-reviewed implementations; their runtime checks remain pending. A successful response contains a UUID order ID; read it at `GET /orders/{orderId}`.

Restarting the application or `docker compose down` preserves the database volume. For independent development examples, explicitly reset only the local fixture:

```bash
docker compose exec -T postgres psql -U orders -d orders \
  -c 'TRUNCATE invoice_document, order_invoice, inventory_adjustment, purchase_order, inventory; INSERT INTO inventory (product_id, available) VALUES (1, 3);'
```

That command deletes local practice orders and resets inventory; do not use it against another environment. Tests use their own temporary database, not the development volume.

To package for a manual run without executing tests:

```bash
./mvnw -DskipTests package
java -jar target/order-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Packaging with skipped tests does not complete the requirement. After solving it, use `./mvnw verify` normally. Use `mvnw.cmd` on Windows.

## Review and progress

Track the task in the [requirements index](requirements/README.md). After completing the marked gaps, run both sets of checks and explain how the boundary and stock-write behavior protect the rules. Request review before marking the requirement done.

Future exercises can reuse this application or introduce another application when the topic requires a service boundary. All projects share the parent repository's Git history.

References: [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html), [Flyway startup migrations](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Lombok setup](https://projectlombok.org/setup/maven).
