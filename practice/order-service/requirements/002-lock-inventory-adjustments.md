# 002 — Lock inventory before adjusting stock

**Status:** in-review

**Learning:** [Isolation and concurrency control](../../../java/database-transactions-and-spring-transactional.md#isolation--protect-competing-decisions)

**Prerequisite:** the existing inventory model and transaction concepts from [001](001-place-orders.md). Your 001 implementation has passed code review; runtime verification remains pending.

## The requirement

Warehouse staff record damaged, returned, or newly delivered stock. Each adjustment reads the current quantity, computes a new quantity from a signed delta, and records the exact before/after balances with a reason.

Starting at **3**, two concurrent adjustments of **−2** must produce one success and one rejection. The rejected attempt creates no audit record. Adjustments must also coordinate with the existing order-reservation endpoint.

## Supplied API and implementation

| Endpoint | Behavior |
|---|---|
| `POST /inventory/{productId}/adjustments` | Accept `delta` and `reason`; return `201` with the audit record |
| `GET /inventory/adjustments/{adjustmentId}` | Return the audit record or `404` |

Example input: `{"delta":-2,"reason":"damaged stock"}`. The response contains `id`, `productId`, `delta`, `beforeAvailable`, `afterAvailable`, and `reason`.

Validation, transaction scope, balance calculation, range checks, stock writes, audit persistence, schema migration, and errors are supplied. A zero delta or invalid request returns `400`; missing product returns `404`; a negative resulting balance returns `409`.

---

## Reviewed implementation

`InventoryRepository.findForAdjustment` now uses a locking read. It passed code review by inspection; the completed marker and unused imports have been removed. Runtime acceptance checks remain pending. The implementation must:

- Return the current row while protecting the later decision and write from competing writers.
- Return `Optional.empty()` for an absent product.
- Use the existing service transaction and injected database access component.
- Leave the existing guarded order reservation intact. Both features must coordinate through the database.

A plain read followed by the supplied absolute stock write can overwrite another operation's change. A transaction boundary alone does not prevent that stale decision. Do not substitute a process-local Java lock: other processes must be protected too.

## Acceptance checks

| Check | Expected outcome |
|---|---|
| Adjust by −2 from 3 | Stock 1; one audit record with before 3 / after 1 |
| Adjust by −4 from 3 | `409`; stock 3; no audit |
| Adjust an absent product | `404`; no audit |
| Another transaction holds the stock row for writing | Your locking read waits before returning the balance |
| Two adjustments of −2 compete | One `201`, one `409`; stock 1; one audit |
| Adjustment −2 competes with order quantity 2 | One success; stock 1; exactly one order or adjustment record |

The tests hold a stock row temporarily to force contention. Assertions read committed state afterward. The initial starter returned `501 / EXERCISE_INCOMPLETE`. The implementation now has the locking read, but the checks have not been rerun after this change.

## Work and review

You can ask for a direct code review without rerunning tests each time. To check only this exercise:

```bash
./mvnw -Dtest=LockingExerciseTests test
```

For a single check, for example:

```bash
./mvnw '-Dtest=LockingExerciseTests#lockingReadWaitsForAnExistingWriter' test
```

After review, Codex removes completed exercise markers and unused imports. Completion still requires recorded verification evidence; a code-only review is recorded as such.
