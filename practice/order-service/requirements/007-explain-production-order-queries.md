# 007 — Explain production order queries

Status: ready

Learning: [SQL optimisation](../../../database/sql-optimisation.md).

Dependencies: No implementation prerequisite; learn this before tuning 008–011.

## Target and learner-owned work

Start at the TODO 007 in `OrderHistoryRepository.offsetPageV1()`. Keep V1 executable, add your EXPLAIN interpretation as comments near the V1 query, and capture the complete dynamically assembled SQL and bind values for: first page, deep offset (for example 20,000), product/date filter, and a search with few matches. Use `EXPLAIN (ANALYZE, BUFFERS)` on these SELECTs in the development database. Do not change SQL or add indexes yet.

To capture your first plan from a connected psql session started in `practice/order-service/`:

```text
\i scripts/explain-order-history.sql
\set page_offset 20000
\i scripts/explain-order-history.sql
```

This supplied script runs the unfiltered baseline with the API's extra look-ahead row. It does not tune SQL or apply schema changes. Capture filtered variants from `offsetPageV1()` as well; replace JDBC `?` markers with equivalent typed values for the manual query, recording those values with the plan. `EXPLAIN ANALYZE` executes the SELECT, so use the development dataset.

Create `docs/query-evidence/007-plans.md` with sanitised evidence. Explain the plan from data access to output, including estimated versus actual rows, removed rows, loops, cached/read blocks, sort method/memory and whether LIMIT stopped work early. Distinguish a repeated indexed probe from a repeated full scan, and database work from connection waiting, serialization and round trips.

## Acceptance criteria

- Explain the correlated invoice subplan and its loop count using the actual plan. Do not assume a subquery is slow or a join is fast from syntax alone.
- Compare first/deep pages without claiming equal cost because both return 25 rows.
- State one measured bottleneck and a falsifiable optimisation hypothesis for each case.
- Separate correctness observations, measured results and untested assumptions. Include repeated comparable runs and cache-state limitations; do not invent timing targets.

## Supplied application support

Use **Orders → Order history / Order totals by product** in the existing application. `OrderHistoryController` validates bounded inputs and supplies page metadata. `OrderHistoryRepository` contains the actual SQL and inline TODOs. Offset browsing, filters and reporting are working baselines; V1 remains the working baseline; V2 history/report and cursor requests return `501 EXERCISE_INCOMPLETE` until their marked gaps are implemented. Existing `/workspace/orders` and learner work from 006 stay separate.

Optional workload: from `practice/order-service/`, with the Compose database running, run:

```bash
docker compose exec -T postgres psql -U orders -d orders -v ON_ERROR_STOP=1 < scripts/seed-production-query-workload.sql
```

For the rootless database, connect with psql using the existing local credentials and execute `\i scripts/seed-production-query-workload.sql` from that directory. This inserts 30,000 deterministic orders and 6,000 invoices into the real tables, with timestamp ties, missing invoices and skew across up to three existing products. It changes order counts/history, does not reserve stock, skips an empty catalog, and does not reset existing data. Re-running preserves existing fixture records rather than changing their timestamps or quantities. Use a disposable development dataset if existing counts must remain unchanged. Do not run the seed on production.

## V1/V2 record

Preserve the V1 implementation and its initial plan. Add concise V1 comments explaining the observed work, then implement the candidate in V2. Record both SQL versions, bind values, result checks and comparable plans using the [comparison template](../docs/query-evidence/comparison-template.md). V1/V2 query labels are not Flyway migration numbers. Adding indexes can change both versions' plans: retain the pre-index evidence and identify the schema/index state of every run. Do not overwrite V1 or publish learner implementation automatically.

## Verification and publication

Code review and a manual result/plan walkthrough are the default; no new automated exercise tests are supplied. Record PostgreSQL version, dataset counts/distribution, parameters, SQL, plans, result checks and repeated timings. Browser request duration includes more than SQL execution. New indexes belong in a new unapplied migration; inspect the existing versions rather than editing V9 or assuming the next number. Keep secrets and raw customer data out of committed evidence. Completion needs evidence; written SQL alone is insufficient.
