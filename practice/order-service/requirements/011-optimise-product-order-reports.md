# 011 — Optimise product order reports

Status: ready

Learning: [SQL optimisation](../../../database/sql-optimisation.md).

Dependencies: 007 and the existing order/invoice schema; may be attempted independently of cursor implementation.

## Target and learner-owned gap

Complete TODO 011 in `OrderHistoryRepository.productReportV2()`. `productReportV1()` returns correct per-product totals using several correlated subqueries; keep it and write your improvement in `productReportV2()`. Improve measured repeated scanning/aggregation while retaining the report contract. Use the actual **Order totals by product** panel, which loads the report on demand.

## Acceptance criteria

- Keep one row per product, ordered by product ID, limited to the first 100 catalog products. Include zero-order products with zero counts/sums.
- `orders` counts orders, `units` sums ordered quantities, `invoices` counts stored invoices, and `billedMinor` sums invoice amounts in the app's fixed MYR currency. Missing invoices must not remove orders or products.
- Demonstrate that adding joins does not multiply sums or counts. State the grain of intermediate results and check a product with several orders and partial invoicing.
- Compare plans and database work on skewed data. Pre-aggregation is a candidate, not an automatic win for a small selected product set.
- Discuss when an exact synchronous report becomes too costly and a cache/materialized projection fits. Specify freshness, invalidation/refresh, concurrent-writer consistency and a repair path; no fake cached result is supplied. Implement materialization in [012](012-materialize-product-order-reports.md) and Redis caching in [013](013-cache-product-order-reports.md).
- Update `docs/query-evidence/011-report.md`; separate query execution time from reporting payload and browser rendering.

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
