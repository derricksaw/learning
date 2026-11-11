# 004 — Reject stale stock corrections across instances

**Status:** done

**Learning:** [Concurrency across application instances](../../../software-design/distributed-concurrency.md)

**Dependencies:** the existing order service, PostgreSQL, and transaction/conditional-update concepts from 001 and 002. This exercise uses the same application on two ports, sharing one database; no additional service is needed.

## Business requirement

Warehouse staff can correct the absolute stock quantity after a physical count. Two people may load the same product through different application instances. **A correction must be rejected if inventory changed since that person loaded the screen**, including a change from an order or adjustment.

Both staff members read stock 3, version 0. A sets stock to 10 and succeeds, advancing version to 1. B's save of stock 12 with expected version 0 returns `409 / STALE_INVENTORY`; the database remains stock 10, version 1. B must reload and reconsider. This differs from applying a delta and from repeating a request after an unknown response.

## Supplied application

| API | Behavior |
| --- | --- |
| `GET /inventory/{productId}` | Returns `productId`, `available`, and the new `version` field |
| `PUT /inventory/{productId}/stock` | Accepts `available` and `expectedVersion`; returns the saved inventory on success |
| Invalid body, negative quantity/version, or nonpositive product ID | `400` |
| Missing product | `404` |
| Stale expected version | `409 / STALE_INVENTORY` |

The V4 migration adds `inventory.version`, initially 0. Existing reservations and adjustments advance it on successful stock writes; failed operations do not commit an increment. These supporting changes preserve your existing deduction and locking mechanisms.

The controller, validation, service transaction, error mapping, and readback are supplied. The transaction retains a successful update's database protection through readback so the response describes that save. The preliminary existence read supplies a missing-product response; it does not protect the decision. No deletion endpoint is supplied.

## Reviewed implementation

Reviewed `InventoryRepository.replaceIfVersionMatches` in [InventoryRepository.java](../src/main/java/dev/learning/orders/repository/InventoryRepository.java).

The accepted implementation satisfies these requirements:

- Match the product and the expected version in one conditional database write.
- Replace `available` and advance the version together on success.
- Return the actual affected-row count: one for success, zero for a mismatch or absent row.
- Leave stock and version unchanged on mismatch.

Use the existing `jdbc` component. `JdbcTemplate.update(sql, arguments...)` binds arguments to `?` placeholders in order and returns the affected-row count. Do not add a Java lock, a separate version check followed by an unconditional write, or an automatic retry using a newer version. The supplied service handles the returned count and transaction boundary. [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)

The implementation uses one conditional `UPDATE` with product ID and expected version predicates. Its argument order matches the placeholders. The completed TODO and unused imports were removed.

## Review criteria

Code review is the default verification for this exercise. No new automated tests are required.

| Trace during review | Required behavior |
| --- | --- |
| Save stock 10 with expected version 0 | Stock 10, version 1; `200` |
| Another save still expects version 0 | `409`; row unchanged |
| Two instances submit different quantities at the same expected version | Exactly one succeeds; the other conflicts |
| An order reserves stock after the correction screen was loaded | Reservation advances version; correction conflicts |
| An adjustment changes stock after the screen was loaded | Adjustment advances version; correction conflicts |
| Staff reloads, reconsiders, and explicitly submits the current version | New correction may succeed and advances version again |
| Insufficient-stock order fails | Stock and version unchanged |

Explain where the atomic decision occurs, why separate `synchronized` blocks cannot enforce it, and why all writers must participate in version advancement. Distinguish a passed inspection from an observed runtime result.

## Optional manual demonstration with two processes

Start the development database using the application README. In two terminals, run from this directory with the same `DB_PASSWORD` and database settings:

```bash
# Terminal A
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  '-Dspring-boot.run.arguments=--server.port=8080'
```

```bash
# Terminal B
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  '-Dspring-boot.run.arguments=--server.port=8081'
```

Read product 1 through both instances before saving; use the actual returned version rather than assuming 0 on a reused development database.

```bash
curl --fail http://127.0.0.1:8080/inventory/1
curl --fail http://127.0.0.1:8081/inventory/1
```

To demonstrate the reviewed implementation, send these with the same captured version, replacing `VERSION_YOU_READ` with its numeric value:

```bash
curl -i -X PUT -H 'Content-Type: application/json' \
  -d '{"available":10,"expectedVersion":VERSION_YOU_READ}' \
  http://127.0.0.1:8080/inventory/1/stock

curl -i -X PUT -H 'Content-Type: application/json' \
  -d '{"available":12,"expectedVersion":VERSION_YOU_READ}' \
  http://127.0.0.1:8081/inventory/1/stock
```

The first should succeed and the second should conflict even though they reach separate processes. Read inventory again through either instance to see stock 10 and the advanced version. These operations change the local development fixture; do not use a production database.

## Scope and current verification

This is a focused teaching stock-correction endpoint. It omits staff authentication/authorization and correction auditing; a real warehouse application would require both. It does not implement a global carrier quota, a distributed lease, multi-region failover, or idempotent recovery after an unknown response.

Supporting schema and writer changes compiled and passed 17 checks during preparation, including existing transaction and locking checks. The exercise uses code review by default and supplies no new automated test files. The learner's conditional write passed code review by inspection: a successful write increments the version, so a competing write using the old version cannot match; all existing application stock writers also advance that version. The supplied service maps zero affected rows to conflict and keeps readback in its transaction. Completion is based on this recorded code review, not a runtime claim. No tests were rerun for this review, and the optional manual two-process walkthrough has not been performed.
