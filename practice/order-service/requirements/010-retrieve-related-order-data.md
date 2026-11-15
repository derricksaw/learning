# 010 — Retrieve related order data efficiently

Status: ready

Learning: [SQL optimisation](../../../database/sql-optimisation.md).

Dependencies: 007; use a bounded offset or completed cursor page as the parent set.

## Target and learner-owned gap

Complete TODO 010 in `OrderHistoryRepository.offsetPageV2()` and carry the accepted enrichment strategy into cursor pages. Determine whether the baseline correlated invoice check is materially expensive. Compare direct indexed existence/probe, a local join, and a **bounded related-record batch followed by an application merge**. Choose using evidence, cardinality and consistency requirements rather than a blanket JOIN ban.

The current application owns one local PostgreSQL database: local joins are allowed. Separately discuss a production boundary where invoices belong to another service/shard and a local join is unavailable. Explain how a batch API or read projection would change freshness and failure behaviour; no new remote service is required for this exercise.

## Acceptance criteria

- Missing invoices keep their parent order with `hasInvoice=false`; matching invoices yield true. Preserve page identity, size and order before enrichment.
- Do not issue one database/HTTP request per order and do not load the entire invoice table. Bound batch keys to the selected page and avoid empty-list SQL errors.
- Account for the existing unique `order_invoice.order_id` constraint when reasoning about duplication and access paths. A join may still use repeated index probes internally.
- Measure plan loops/buffers plus whole-request query count/round trips for compared implementations. Record extra application memory/merge cost when batching.
- Explain what happens if an invoice is created between two reads. State whether a shared database snapshot is necessary and how a service boundary changes that answer.
- Update `docs/query-evidence/010-related-data.md` with the alternatives and selected tradeoff; do not invent cross-service atomicity.

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
