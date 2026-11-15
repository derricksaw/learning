# 009 — Paginate a changing order history

Status: ready

Learning: [SQL optimisation](../../../database/sql-optimisation.md).

Dependencies: 007 plan basics; offset and cursor comparisons must use the same active dataset and filter contract.

## Target and learner-owned gap

Implement TODO 009 in `OrderHistoryRepository.cursorPage()`. The UI and endpoint already pass `afterCreatedAt`/`afterId`, request `limit + 1` rows, trim the extra row and derive continuation metadata. Do not move the boundary to the look-ahead row. Cursor mode must work both with no boundary (first page) and with both boundary values (next page).

Derive continuation for **time descending, UUID ascending**. The illustrative all-descending shipment example in the note is not a drop-in predicate. Reapply every product/date/search filter from the current request. The supplied UI resets the boundary on applied filter changes and retains server timestamp strings without truncating precision.

## Acceptance criteria

- At fixed data, traverse all matches in the required order without missing/duplicating rows, including timestamps shared by more than one page, missing invoices, empty results and the final page.
- Capture first/deep-offset/cursor plans with equivalent page positions and justify supporting access paths. A cursor clause alone does not guarantee bounded scan work.
- Preserve `items`, `hasMore`, `nextCursor`, `nextOffset`; cursor mode leaves `nextOffset` null. No exact total count is required.
- Explain and demonstrate insertion of a newer order between requests: offset positions may shift; cursor continuation keeps its boundary. Define behaviour for deletes and mutable ordering keys without claiming a frozen snapshot.
- Preserve validation for half-cursors, invalid UUID/timestamps, invalid modes and limits. Describe cursor/filter binding and authorization if the app becomes multi-tenant.
- Update `docs/query-evidence/009-pagination.md`. No fake successful cursor response or fallback to OFFSET for cursor requests.

## Supplied application support

Use **Orders → Order history / Order totals by product** in the existing application. `OrderHistoryController` validates bounded inputs and supplies page metadata. `OrderHistoryRepository` contains the actual SQL and inline TODOs. Offset browsing, filters and reporting are working baselines; cursor requests return `501 EXERCISE_INCOMPLETE` until 009 is implemented. Existing `/workspace/orders` and learner work from 006 stay separate.

Optional workload: from `practice/order-service/`, with the Compose database running, run:

```bash
docker compose exec -T postgres psql -U orders -d orders -v ON_ERROR_STOP=1 < scripts/seed-production-query-workload.sql
```

For the rootless database, connect with psql using the existing local credentials and execute `\i scripts/seed-production-query-workload.sql` from that directory. This inserts 30,000 deterministic orders and 6,000 invoices into the real tables, with timestamp ties, missing invoices and skew across up to three existing products. It changes order counts/history, does not reserve stock, skips an empty catalog, and does not reset existing data. Re-running preserves existing fixture records rather than changing their timestamps or quantities. Use a disposable development dataset if existing counts must remain unchanged. Do not run the seed on production.

## Verification and publication

Code review and a manual result/plan walkthrough are the default; no new automated exercise tests are supplied. Record PostgreSQL version, dataset counts/distribution, parameters, SQL, plans, result checks and repeated timings. Browser request duration includes more than SQL execution. New indexes belong in a new unapplied migration; inspect the existing versions rather than editing V9 or assuming the next number. Keep secrets and raw customer data out of committed evidence. Completion needs evidence; written SQL alone is insufficient.
