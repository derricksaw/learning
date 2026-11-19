# 014 — Model packing progress

**Status** ready

Read [Domain-driven design in Fulfillment](../../../software-design/domain-driven-design.md#domain-driven-design-in-fulfillment) before starting.

Fulfillment records an absolute packed quantity against an order. Sales owns the ordered quantity. A packing update can advance progress up to that quantity. Repeating the current total is safe, including after packing finishes. A lower total is a rejected transition.

## Supplied pieces

- The existing order placement flow and development inventory supply orders to pack. Place an order with enough available units for the walkthrough, then use its ID.
- The packing controller exposes `GET /orders/{orderId}/packing` and `PUT /orders/{orderId}/packing` on either API instance. The existing workspace proxies also expose these paths under `/api/a` and `/api/b`.
- `PackingService` runs the command inside Spring's `TransactionTemplate`. Its callback loads the current aggregate, invokes the transition and saves changed state. An unchecked exception rolls back the callback.
- `JdbcPackingRepository` translates Sales order data and the progress row into a Fulfillment model. It locks the parent order before reading progress for a command, using a second SQL statement to see the committed result of a competing update. This uses PostgreSQL's default Read Committed isolation. Migration V10 creates the progress table. An absent row represents zero packed.
- Spring MVC uses `@PutMapping` to route the command and `@RequestBody` to deserialize its JSON. `@Valid` and `@NotNull` reject a missing packed quantity before the command runs. The controller maps business rejections to the existing API error format. See [Spring MVC request bodies](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html) and [programmatic transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html).

## Your task

Implement `recordPackedQuantity` at `TODO(014)` in [PackingOrder.java](../src/main/java/dev/learning/orders/packing/PackingOrder.java). Keep the decision independent of HTTP, Spring and JDBC. Use the supplied domain rejection type for invalid transitions. Explain which rule belongs to this model and why both API instances also need the repository's locking protocol.

## Acceptance

| Case | Expected result |
|---|---|
| GET for an existing order before packing | 200 with ordered quantity, packed quantity 0 and `complete: false` |
| PUT with `{"packedQuantity":2}` for an order of 3 | 200 and total 2 |
| Repeat that PUT immediately | 200 and same state |
| PUT 3, then retry 3 | Both respond 200 with `complete: true` |
| PUT a negative total, a total above 3 or a lower total after progress | 409 `PACKING_REJECTED` and unchanged stored state |
| GET or well-formed PUT for an unknown order | 404 |
| Send competing totals 2 and 3 from zero on different API instances | Final total 3. Both can succeed when 2 acquires the lock first. If 3 acquires it first, 2 receives 409. |

Start with the [Order Lab setup](../README.md). Before completing the TODO, a well-formed PUT for an existing order returns 501 `PACKING_UNFINISHED`. After implementation, use `curl` or an HTTP client to walk through the cases and compare with GET. Review the saved code and trace both lock orders as the default exercise verification.
