# 012 — Materialize product order reports

Status: ready

Learning: [Database design and SQL optimization (interactive site)](../../../database/database-design-and-sql-optimization.html).

Dependencies: 011's understood live report and V1/V2 evidence; a functioning live implementation for the chosen version.

## Learner-owned gaps

Complete TODO 012 in `OrderReportReadService.materialized()` and `refreshMaterialized()`. Start the database changes from the [migration TODO template](../docs/query-evidence/templates/materialized-report-migration.sql). Copy it into a new forward migration when you implement the materialized report and freshness metadata. Use the existing report contract: first 100 products by ID, zero-child products retained, correct order/unit/invoice totals and MYR minor-unit sums. Start from a valid report query, not a cache of incorrect totals.

The UI calls `POST /workspace/order-report-view/refresh?version=v1|v2`; reads use `source=materialized`. Until implemented these return 501. Do not silently return live SQL and label it materialized.

## Acceptance criteria

- Compare the materialized read against the selected live query at the represented source snapshot. Explain changes that commit after the refresh snapshot.
- Keep a reliable generation/refresh time; document whether it means refresh start or completion and how it relates to source freshness. Coordinate metadata with successful refresh so it does not claim success after failure.
- Demonstrate a new order/invoice, an old materialized result, a successful refresh and the updated result. Never pretend source changes are propagated automatically.
- Compare ordinary and concurrent refresh, including reader blocking, eligible unique-index prerequisites, initial population and one-refresh-at-a-time behaviour for a view.
- Budget refresh duration, frequency, database I/O, index/storage cost and failed refresh recovery. A fast read alone is insufficient evidence.
- Preserve version/source labels honestly. If one materialized definition serves equivalent V1/V2 contracts, document its actual lineage; do not pretend two distinct views were benchmarked.
- Record `docs/query-evidence/012-materialized.md`, including stale age, rows, source/query version and refresh measurements.

## Supplied pieces and verification

The real **Orders → Order totals by product** panel has V1/V2 and source selectors, reload, materialized-view refresh and cache invalidation controls. `OrderReportReadService` supplies explicit learner-owned 501 paths, plus the existing `JdbcTemplate`, `StringRedisTemplate` and live report repository. V1 live SQL works; V2 is the learner candidate. Existing Redis configuration and shared A/B instances are reused. No materialized view, cache-aside algorithm or refresh/invalidation solution is supplied.

The read endpoint `/workspace/order-report-view?source=...&version=...` returns `{items, source, version, generatedAt, cacheStatus}`. Live generation time records query completion. Materialized/cache results must preserve the actual computation/refresh time instead of claiming a new result on every read. Cache status must report the actual hit/miss/fallback policy; live SQL uses `bypass`.

Use the shared [workload instructions](007-explain-production-order-queries.md) and [V1/V2 comparison template](../docs/query-evidence/comparison-template.md). Code review plus a manual concurrency/failure walkthrough is the default; no new automated exercise tests are supplied. Record both read and refresh/fill costs, repeated timings, result/freshness checks and recovery behaviour. Keep the solution local unless explicitly asked to publish it.
