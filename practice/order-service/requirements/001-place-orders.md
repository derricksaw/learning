# 001 — Place orders without overselling

**Status:** in-review

**Learning:** [Database transactions and Spring @Transactional](../../../java/database-transactions-and-spring-transactional.md)

**Depends on:** the supplied PostgreSQL application and passing support checks

## The product requirement

A customer orders units of an existing product. A successful order must be stored together with the corresponding stock deduction. Failed or competing attempts must not leave partial orders or oversold inventory.

Start with **product 1, available stock 3**. Ordering **2** must create one order and leave **1** available.

---

## API contract

| Endpoint | Behavior |
|---|---|
| `POST /orders` | Accept `productId` and `quantity`; return `201` with the persisted order |
| `GET /orders/{orderId}` | Return the stored order, or `404` |
| `GET /inventory/{productId}` | Return the product ID and available stock, or `404` |

An order response contains `id`, `productId`, `quantity`, and `status` (`PLACED`). Choose a stable ID representation and document it. Inventory returns `productId` and `available`.

| Rejected request | Response |
|---|---|
| Missing, malformed, or nonpositive product ID or quantity | `400` |
| Product does not exist | `404` |
| Available stock cannot satisfy the order | `409` |

Errors must contain a machine-readable code and a useful message. Internal failures return a server error without exposing stack traces or connection details. There is no customer identity or authentication in this first slice.

---

## Acceptance criteria

| ID | Scenario | Evidence required |
|---|---|---|
| AC1 | Product 1 has stock 3; order 2 | `201`; exactly one stored order for 2; stock 1 |
| AC2 | Order 4 from stock 3 | `409`; stock 3; no new order |
| AC3 | Request has zero/negative quantity or references a missing product | Appropriate error; no stock or order changes |
| AC4 | Two concurrent requests each order 2 from stock 3 | One `201`, one `409`; one order; stock 1 |
| AC5 | A controlled failure occurs after a stock-changing write and before the operation completes | Whole operation aborts; stock restored; no committed order |
| AC6 | Application stops and restarts against the same database | Committed orders and inventory remain readable |
| AC7 | Invalid quantities or negative stock are written directly to the database | Schema constraints reject them |

AC5 must be deterministic and reproducible in a test; keep failure injection in test infrastructure rather than adding a public failure endpoint. Verify committed state from outside the failed transaction.

## Supplied pieces and your gaps

| Supplied by Codex | Complete yourself |
|---|---|
| Order/inventory controllers, validated DTO, and JSON errors | Transaction boundary in `OrderService.place` (code-reviewed) |
| PostgreSQL configuration, Flyway schema, development fixture | Guarded update in `InventoryRepository.reserveStock` (code-reviewed) |
| Order persistence, inventory reads, and schema constraints | Explain why your choices hold under failures and competing writes |
| Real-PostgreSQL test infrastructure and failure injection | Make the four transaction exercise checks pass |

The two implementation points have been completed and reviewed by inspection. Their exercise markers were removed. Framework syntax and API explanations remain available directly.

The support checks cover HTTP reads, invalid input, missing resources, database constraints, and startup. The exercise checks cover AC1, AC2, AC4, and AC5. AC3 and AC7 are supported by the supplied tests; demonstrate AC6 manually by restarting against the same development database. Startup commands and local database setup are in the [application README](../README.md).

```bash
./mvnw -Dtest=OrderServiceApplicationTests,ScaffoldTests test
./mvnw -Dtest=TransactionExerciseTests test
```

The original starting point had passing support checks and exercise failures at `EXERCISE_INCOMPLETE`. Both gaps now have implementations. Runtime checks have not been rerun after code review; do not infer a verified pass from the removed markers.

Payment, messaging/outbox, request deduplication, cancellation, and a frontend are later features. Repeated submissions are independent attempts until a future requirement adds request identity.

## Review evidence

When asking for review, provide:

- The test command and results, mapped to AC1–AC7.
- Example successful and rejected HTTP requests with resulting database state.
- A short explanation of which rules are enforced by the schema, service logic, and concurrency control.

Preparation evidence: the original support checks passed against PostgreSQL 17.11. Subsequent code review accepted the guarded deduction and service transaction. The updated exercise checks and restart demonstration remain unverified.
