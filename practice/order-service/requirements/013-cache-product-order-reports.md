# 013 — Cache product order reports in Redis

Status: ready

Learning: [Database design and SQL optimization (interactive site)](../../../database/database-design-and-sql-optimization.html).

Dependencies: 011's correct live report and V1/V2 evidence; understand 012's freshness tradeoff. Materialized-view completion is not mandatory if Redis fills from live SQL.

## Learner-owned gaps

Complete TODO 013 in `OrderReportReadService.cached()` and `invalidateCache()`. Implement cache-aside for this real report using the supplied `StringRedisTemplate`. Define a versioned key/payload, serialization, TTL, generation time and concurrency/failure policy. Use a consistent report source for fills and document it.

The UI reads with `source=redis` and invalidates through `DELETE /workspace/order-report-view/cache?version=v1|v2`. Until implemented these return 501. Do not return a live result while reporting a cache hit.

## Acceptance criteria

- Show cold miss → computed result → warm hit → expiry/invalidation → fresh fill. Hit/miss/fallback status and generation time reflect the actual path.
- Namespace the key by query/payload version and all result-changing dimensions. Keep V1 and V2 payloads from being mixed, and account for future tenant/authorization scoping.
- Give entries a bounded lifetime. Define whether freshness is TTL-only, write invalidation, version checking, or a documented combination; do not claim TTL guarantees immediate freshness.
- Demonstrate concurrent misses and a database write during a delayed fill. Explain cache stampede handling and stale-fill-after-invalidation behaviour, including limits of temporary ownership.
- Define Redis outage and malformed/old-payload behaviour. A live fallback needs database protection; a strict failure must be explicit. Rebuilding after Redis loss must not lose authoritative PostgreSQL state.
- Invalidate only this report's keys. Do not use FLUSHALL or a blocking whole-keyspace operation as an application invalidation mechanism.
- Compare warm hit latency, miss/fill latency, Redis round trips, serialization and database work. State when caching is less useful than a tuned live query or a materialized report.
- Record `docs/query-evidence/013-cache.md`, including stale-data bounds, failure evidence and the selected invalidation policy.

## Supplied pieces and verification

The real **Orders → Order totals by product** panel has V1/V2 and source selectors, reload, materialized-view refresh and cache invalidation controls. `OrderReportReadService` supplies explicit learner-owned 501 paths, plus the existing `JdbcTemplate`, `StringRedisTemplate` and live report repository. V1 live SQL works; V2 is the learner candidate. Existing Redis configuration and shared A/B instances are reused. No materialized view, cache-aside algorithm or refresh/invalidation solution is supplied.

The read endpoint `/workspace/order-report-view?source=...&version=...` returns `{items, source, version, generatedAt, cacheStatus}`. Live generation time records query completion. Materialized/cache results must preserve the actual computation/refresh time instead of claiming a new result on every read. Cache status must report the actual hit/miss/fallback policy; live SQL uses `bypass`.

Use the shared [workload instructions](007-explain-production-order-queries.md) and [V1/V2 comparison template](../docs/query-evidence/comparison-template.md). Code review plus a manual concurrency/failure walkthrough is the default; no new automated exercise tests are supplied. Record both read and refresh/fill costs, repeated timings, result/freshness checks and recovery behaviour. Keep the solution local unless explicitly asked to publish it.
