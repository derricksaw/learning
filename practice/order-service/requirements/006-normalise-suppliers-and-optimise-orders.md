# 006 — Normalise suppliers and optimise order reads

Status: ready

Learning: [Database normalisation and SQL optimisation](../../../database/database-normalisation-and-sql-optimisation.md).

## Scope and supplied pieces

Work in the running Order Lab, using its existing PostgreSQL database, Inventory screen and Orders screen. No separate lab or service. Supplier contacts are intentionally repeated in inventory; the actual `/workspace/orders` read has deliberately expensive SQL. Both features work before optimisation.

The app supplies supplier columns and UI, a correct slow query, and optional repeatable workload data. This exercise is independent of unfinished invoice creation in 005: the fixture inserts simulated invoices directly. Existing 001–005 implementations must be preserved.

## Your implementation gaps

1. Normalise current supplier contacts. Business rule: each product has one supplier; `supplier_code` identifies one current name and email. A supplier can exist without products. Add a new forward Flyway migration, resolve or reject conflicting source contacts, backfill and enforce references. Do not edit an already-applied migration. The inline TODO in `V8__inventory_supplier_contacts.sql` marks this design debt, not a request to rewrite V8.
2. Update the inline TODO in `WorkspaceController.inventory()` to read the new model while preserving `supplierCode`, `supplierName`, `supplierEmail` and all existing JSON fields. Check existing inventory insertion paths and defaults; order and stock operations must still work.
3. Optimise the inline TODO in `WorkspaceController.orders()` and add justified indexes through a new migration if needed. Keep the latest 100 orders ordered by `created_at DESC, id ASC`, current product name, and the correct invoice-existence flag. Handle zero orders, missing invoices and tied timestamps. Explain why your chosen query and access paths reduce work.

No target schema DDL, tuned SQL or indexes are supplied. Request hints or review when ready. The existing `JdbcTemplate.query` call executes SQL and maps each result row into the response record; preserve the mapper's column labels and types when changing the SQL.

## Run and observe

Start the app using its [README](../README.md). Restart after backend changes so Flyway applies new migrations. Inventory now displays supplier contacts; Orders and Overview use the same slow read. Optional workload insertion changes the real app's counts and order list, but does not reserve stock. Use only a disposable development dataset if you want the existing counts preserved.

From `practice/order-service/`, with the Compose database running:

```bash
docker compose exec -T postgres psql -U orders -d orders -v ON_ERROR_STOP=1 < scripts/seed-query-workload.sql
```

For the rootless stack, use its bundled `psql` with the local connection configuration described in the runbook; do not copy credentials into tracked files. The script adds 20,000 deterministic orders and 4,000 invoices if inventory exists. Re-running it does not duplicate those records. It deliberately skips insertion if the catalog is empty.

Capture the exact query from `WorkspaceController.orders()` using `EXPLAIN (ANALYZE, BUFFERS)` in psql before and after your change. Record PostgreSQL version, row counts, repeated timings and plan evidence, including subplan loops, buffers and sort work. Compare both warm runs and equivalent data; don't claim a speedup from one cache-warming run. At small volumes, focus on work and plan structure rather than a fixed millisecond target. Fixture timestamps are fixed, so later real orders can appear above them.

## Acceptance and review

- A supplier contact changes once and every referencing product returns the same contact; a supplier survives removal of its last product. Missing references are rejected.
- The migration preserves product IDs, stock, versions, orders and invoices. Describe the plan for conflicting source contacts and concurrent old writers.
- Both API prefixes and the existing screens continue to work, including empty results and deterministic ordering.
- Before/after results are identical on the same dataset; the plan supports your performance explanation. Explain index write/storage costs.
- Normalisation and tuning are learner work. Default verification is code review plus the supplied manual walkthrough; no new exercise tests are required. Existing backend and frontend checks validate support compatibility, not completion of 006.

Preparation evidence: the frontend production build passed. The existing backend suite ran 28 checks: 27 passed; the health endpoint check failed because Redis was unavailable on port 6380. Flyway applied V8 in the embedded PostgreSQL checks. The new workspace queries, optional workload script and rendered screens were not runtime-verified; query correctness was inspected. Completion remains pending learner implementation and review.
