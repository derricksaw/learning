# API contract

Local API instances listen at `http://localhost:8080` and `http://localhost:8081`. The frontend uses `/api/a` and `/api/b` through its same-origin proxy. Mutations share the same PostgreSQL database; no client-side mock state is written in their place.

## Endpoints

| Method and path | Request / result |
| --- | --- |
| `GET /actuator/health` | Readiness of database and Redis connectivity |
| `GET /workspace/summary` | Total orders, available units, low-stock products, invoices |
| `GET /workspace/inventory` | Catalog metadata, availability, reorder point and version |
| `GET /workspace/orders` | Latest 100 orders, product names, timestamps, invoice existence |
| `GET /workspace/invoices` | Latest 100 invoices, totals and document existence |
| `GET /inventory/{id}` | `{productId, available, version}` |
| `POST /inventory/{id}/adjustments` | `{delta, reason}` → `201` audit with before/after quantities |
| `GET /inventory/adjustments/{id}` | Stored adjustment audit |
| `PUT /inventory/{id}/stock` | `{available, expectedVersion}` → current saved inventory or `409` |
| `POST /orders` | `{productId, quantity}` → `201` with order UUID and `PLACED` status |
| `GET /orders/{id}` | Stored order |
| `POST /orders/{id}/shipping-labels` | Simulated carrier label; no request body |
| `POST /orders/{id}/invoice` | Create once or return durable invoice; exercise 005 |
| `GET /orders/{id}/invoice` | Stored invoice or `404` |
| `POST /orders/{id}/invoice/document` | Stored or newly rendered demo text document; exercise 005 |

`available`, `quantity`, and `delta` are integer counts. A correction's `expectedVersion` must come from the caller's snapshot. Invoice amounts are integer minor units with a currency code. The order ID is the invoice business identity; this exercise does not implement a generic idempotency-key header.

## Errors

Errors use a consistent body:

```json
{"code":"STALE_INVENTORY","message":"Inventory changed since it was read; reload and reconsider the correction"}
```

| HTTP status | Meaning |
| --- | --- |
| `400 INVALID_REQUEST` | Invalid fields, IDs or supported ranges |
| `404 NOT_FOUND` | Missing product, order, invoice or audit |
| `409 INSUFFICIENT_STOCK` | Reservation or adjustment would exhaust stock |
| `409 STALE_INVENTORY` | Snapshot no longer matches; reconsider rather than silently replaying |
| `409 DOCUMENT_BUSY` | Participating worker currently owns document generation |
| `501 EXERCISE_INCOMPLETE` | Explicit learner-owned gap; UI must not show success |
| `502 CARRIER_UNAVAILABLE` | Carrier simulator reported a failure |
| `503 CARRIER_BUSY` | This instance has no carrier capacity; no carrier call was made |
| `503 COORDINATION_UNAVAILABLE` | Redis is unavailable when ownership is needed |
| `500 INTERNAL_ERROR` | Unexpected server failure; no raw exception is returned |

No operation is retried automatically by the frontend. A network timeout can leave the result uncertain; inspect durable state before repeating an externally consequential operation. The current external operations are simulators, so they purchase nothing.

## Exercise 006 read models

`GET /workspace/inventory` additionally returns `supplierCode`, `supplierName`, and `supplierEmail` per item. These are current supplier contacts displayed in Inventory. `GET /workspace/orders` retains its existing contract and intentionally expensive baseline SQL for tuning. Both routes remain available through `/api/a` and `/api/b`.

## Production query exercises 007–011

`GET /workspace/order-history` returns `{items, hasMore, nextCursor, nextOffset}`. Each item has the existing Order fields. Query parameters:

| Parameter | Contract |
|---|---|
| `limit` | 1–100, default 25; server fetches at most one extra look-ahead row |
| `mode` | `offset` (working baseline) or `cursor` (501 until exercise 009) |
| `offset` | 0–1,000,000, default 0; must be 0 in cursor mode |
| `productId` | Optional positive catalog product ID |
| `from`, `to` | Optional inclusive UTC calendar dates in YYYY-MM-DD; from must not exceed to |
| `q` | Optional literal case-insensitive product-name substring, max 120 characters |
| `afterCreatedAt`, `afterId` | Both absent or both present; only allowed in cursor mode. ISO offset timestamp and UUID. |

Ordering is `created_at DESC, id ASC`. With more results, `nextCursor` is `{createdAt, id}` from the last returned row, preserving timestamp precision; `nextOffset` is supplied only in offset mode. There is no exact total count. Invalid parameters return `400 INVALID_REQUEST`. Unimplemented cursor requests return `501 EXERCISE_INCOMPLETE`, including the first cursor page.

`GET /workspace/order-report` returns the first 100 catalog products ordered by product ID with `{productId, productName, orders, units, invoices, billedMinor}`. Zero-child products have zero totals. Amounts are integer minor units in the app's fixed MYR currency. Both endpoints are available through `/api/a` and `/api/b`; existing endpoints keep their contracts.

## V1/V2 query comparison and report sources

History and the existing list-shaped `/workspace/order-report` accept `version=v1|v2`, default `v1`. V1 preserves the executable baseline. V2 history and V2 live reports return `501 EXERCISE_INCOMPLETE` until implemented. Cursor mode belongs to V2; a V1 cursor request explicitly returns 501 rather than silently changing the baseline.

`GET /workspace/order-report-view?source=live|materialized|redis&version=v1|v2` returns `{items, source, version, generatedAt, cacheStatus}`. `items` retains the existing report row fields. Live V1 works with cacheStatus `bypass`; materialized reads/refresh and Redis cache reads/invalidation are learner-owned 501 paths. Generation time must reflect computation or refresh, not merely a cache-hit request time. Unknown versions/sources return 400.

`POST /workspace/order-report-view/refresh?version=v1|v2` requests materialized refresh; `DELETE /workspace/order-report-view/cache?version=v1|v2` requests scoped cache invalidation. Successful learner implementations return 204. These are local learning endpoints; no materialization/cache algorithm or production authorization boundary is supplied.
