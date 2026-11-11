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
