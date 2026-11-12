# Requirements

Each requirement is a focused exercise linked to what you learned. Supporting application pieces and tests are supplied; you complete the marked topic-related gaps and explain the resulting behavior.

| ID | Requirement | Learning | Status |
|---|---|---|---|
| 001 | [Place orders without overselling](001-place-orders.md) | Transaction boundary and concurrent stock reservation | in-review |
| 002 | [Lock inventory before adjusting stock](002-lock-inventory-adjustments.md) | Pessimistic row locking and read-decide-write decisions | in-review |
| 003 | [Protect carrier calls with local CAS capacity](003-claim-local-capacity.md) | Atomic capacity claims and claim lifetime around carrier calls | in-review |
| 004 | [Reject stale stock corrections across instances](004-distributed-stock-corrections.md) | Shared database version checks across separate application processes | done |
| 005 | [Create an invoice once and coordinate rendering](005-invoice-idempotency-and-redis-lease.md) | Durable business-key idempotency versus an owner-checked Redis lease | ready |
| 006 | [Normalise suppliers and optimise order reads](006-normalise-suppliers-and-optimise-orders.md) | Fact ownership and measured tuning of the real Inventory and Orders features | ready |

004 is complete by code review: the guarded SQL and participating writers were inspected; its optional two-process demonstration was not run.

**Progress:** ready → in-progress → in-review → done. Keep the status here and in the requirement consistent. “Done” requires review and recorded evidence, not just written code. Requirements 001 and 002 have passed code review by inspection; their runtime checks remain pending, and 001 also needs its restart demonstration. Both the CAS component and shipping-label integration in 003 passed code review; runtime verification of the completed integration remains pending.

Future topics can introduce duplicate-request handling, order cancellation, asynchronous fulfillment, or production telemetry when those concepts are learned. These are possible extensions, not extra acceptance criteria for 001.
