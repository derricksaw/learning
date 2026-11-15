# 008 — Optimise order filters, search and indexes

Status: ready

Learning: [SQL optimisation](../../../database/sql-optimisation.md).

Dependencies: 007 baseline evidence; 006 supplier changes are not required by the new history endpoint.

## Target and learner-owned gaps

Complete TODO 008 in `OrderHistoryRepository.offsetPage()`. Improve measured date-filter, search and ordered-page access paths. The baseline intentionally extracts a UTC date from timestamps and performs a literal case-insensitive substring search. Preserve the product filter and parameter binding.

Evaluate composite/partial/covering or expression indexes only where the workload supports them; discuss ordinary B-tree limitations for substring matching. A search extension is optional and requires a deployment/fallback decision. Do not turn substring matching into prefix matching merely to claim an index win.

## Acceptance criteria

- Product equality, inclusive UTC `from`/`to` days, and literal case-insensitive product-name substring search return equivalent results. Include a midnight boundary, empty match and `%`/`_` search inputs.
- The result order remains `created_at DESC, id ASC` with at most the requested number of displayed rows. Page look-ahead remains bounded.
- Record before/after plans on frequent and rare filter values; explain selectivity, statistics and any remaining sort. Do not force planner settings to manufacture acceptance.
- Justify chosen indexes against existing ones; discuss storage/write cost, safe build/deployment and rollback. Do not add every possible index.
- Keep page sizes and offsets bounded, bind values, and retain existing 400 handling. Update `docs/query-evidence/008-filters.md`.

## Supplied application support

Use **Orders → Order history / Order totals by product** in the existing application. `OrderHistoryController` validates bounded inputs and supplies page metadata. `OrderHistoryRepository` contains the actual SQL and inline TODOs. Offset browsing, filters and reporting are working baselines; cursor requests return `501 EXERCISE_INCOMPLETE` until 009 is implemented. Existing `/workspace/orders` and learner work from 006 stay separate.

Optional workload: from `practice/order-service/`, with the Compose database running, run:

```bash
docker compose exec -T postgres psql -U orders -d orders -v ON_ERROR_STOP=1 < scripts/seed-production-query-workload.sql
```

For the rootless database, connect with psql using the existing local credentials and execute `\i scripts/seed-production-query-workload.sql` from that directory. This inserts 30,000 deterministic orders and 6,000 invoices into the real tables, with timestamp ties, missing invoices and skew across up to three existing products. It changes order counts/history, does not reserve stock, skips an empty catalog, and does not reset existing data. Re-running preserves existing fixture records rather than changing their timestamps or quantities. Use a disposable development dataset if existing counts must remain unchanged. Do not run the seed on production.

## Verification and publication

Code review and a manual result/plan walkthrough are the default; no new automated exercise tests are supplied. Record PostgreSQL version, dataset counts/distribution, parameters, SQL, plans, result checks and repeated timings. Browser request duration includes more than SQL execution. New indexes belong in a new unapplied migration; inspect the existing versions rather than editing V9 or assuming the next number. Keep secrets and raw customer data out of committed evidence. Completion needs evidence; written SQL alone is insufficient.
