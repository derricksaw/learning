# Database normalisation and SQL optimisation

**Normalisation decides where facts belong. Optimisation reduces the work needed to answer a query correctly.** Learn them together: a well-structured model still needs suitable access paths.

Start with the worked example below, then complete [006 in Order Lab](../practice/order-service/requirements/006-normalise-suppliers-and-optimise-orders.md). It changes the actual Inventory and Orders screens.

## One fact, one authority

Imagine a contact card photocopied into every product folder. Changing one copy leaves conflicting instructions. Normalisation gives the contact an authoritative record and makes products refer to it. The analogy describes ownership; database constraints and transactions still have to enforce the relationships.

| Product | Supplier code | Current supplier name | Current email |
|---|---|---|---|
| Tote | S7 | North Supply | old@north.example |
| Mug | S7 | North Supply | old@north.example |

Updating only Tote to `new@north.example` makes the supplier ambiguous. Deleting both products also deletes the only supplier contact; inserting a supplier requires inventing a product. These are update, deletion and insertion anomalies.

The rule `supplier_code → supplier_name, supplier_email` means equal codes must have equal current details in every valid state. It comes from the business rule, not a coincidence in today's rows. A candidate key is a minimal set identifying a row; a superkey can include extra attributes. A chosen primary key is one candidate key. [Functional dependencies](https://www.cs.rpi.edu/academics/courses/fall24/csci4380/fall2024/course_notes/normalization.html)

## From repeating groups to normal forms

Use a separate illustrative sales model: each order has numbered lines, one customer, and one current customer name; each product has one current name. A line's quantity and sale price belong to that specific sale.

| Stage | Problem removed | Resulting responsibility |
|---|---|---|
| 1NF | Lists such as `product_ids = "4,9"` or repeating product columns | One line per row, values appropriate to their domains |
| 2NF | Non-prime attributes depending on only part of a candidate key | Order facts leave the table keyed by `(order_id, line_no)` |
| 3NF | Dependencies through non-key determinants | Current customer details leave the order table |
| BCNF | Any nontrivial dependency whose determinant is not a superkey | Stronger rule, including dependencies between overlapping keys |

A prime attribute belongs to some candidate key. Formally, 3NF permits `X → A` when the dependency is trivial, X is a superkey, or A is prime. Check **all candidate keys**: adding an artificial ID does not remove existing dependencies. BCNF can require decompositions that lose dependency preservation. [Normalisation overview](https://opentextbc.ca/dbdesign01/chapter/chapter-12-normalization/)

A worked decomposition, shown as tables and their keys:

| Table | Key | Facts owned here |
|---|---|---|
| Customer | customer_id | current_customer_name |
| SalesOrder | order_id | customer_id, placed_at |
| Product | product_id | current_product_name |
| SalesLine | order_id, line_no | product_id, quantity, unit_price_at_sale |

For order 42, line 1 records product 9, quantity 2, price 1200. Raising product 9's current catalogue price to 1500 must leave that sale at 2400. Historical sale price and current catalogue price are different facts. Similarly, a receipt's customer-name snapshot may intentionally differ from the customer's current name.

A valid split must be **lossless**: joining reconstructs the original facts without inventing combinations. Preserve enforceable dependencies where possible. Splitting sales into independent order-to-product and order-to-quantity tables would lose which quantity belongs to which product. Keep the line identity and its associated facts together. [Decomposition](https://www.cs.rpi.edu/academics/courses/fall24/csci4380/fall2024/course_notes/normalization.html)

## Make the model enforceable

Schema design is developer work. PostgreSQL enforces declared primary keys, uniqueness, non-nullability and foreign keys; it does not infer that a supplier code determines an email. Primary and unique constraints create supporting unique indexes; referencing foreign-key columns do not automatically get indexes. [Constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)

When migrating live data, first find codes with conflicting contacts. Do not silently pick an arbitrary winner. Define the resolution rule, backfill, validate references, switch readers and writers, then retire old fields. Concurrent old writers can reintroduce inconsistency during a rolling deployment: coordinate the transition or temporarily maintain both representations transactionally. See [transaction boundaries](../java/database-transactions-and-spring-transactional.md).

Denormalise only for a measured need. A cached order total or supplier projection needs an owner, refresh mechanism, freshness contract and repair path. A join alone is not evidence that the model needs duplication.

## Investigate the work before rewriting SQL

| Step | Evidence to capture | Decision it supports |
|---|---|---|
| Fix the contract | Exact rows, duplicates, nulls, ordering and page size | What must survive a rewrite |
| Reproduce | Representative volume, skew and parameters | Whether the slow case is realistic |
| Read the plan | Estimates versus actual rows, loops, buffers and sorts | Where work multiplies |
| Change one cause | Query shape, statistics or an index | Whether the hypothesis explains the cost |
| Compare | Same results and comparable repeated runs | Whether the change helps the real workload |

Separate SQL execution from network latency, connection-pool waiting and application serialization; see [request investigation](../production/production-request-lifecycle.md).

In PostgreSQL 17, `EXPLAIN` shows estimates; `EXPLAIN (ANALYZE, BUFFERS)` executes the statement. Costs are planner units, not milliseconds. Actual rows and time are per-loop averages: 3 rows across 800 loops means about 2400 rows processed at that node. Buffer hits still represent work. Parent metrics include child work, so do not add every node's metrics. `ANALYZE table_name` refreshes statistics; it is distinct from the EXPLAIN option. [Reading plans](https://www.postgresql.org/docs/17/using-explain.html)

<details>
<summary>Show illustrative plan-reading SQL</summary>

```sql
-- Illustrative; adapt to a representative development database.
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, created_at
FROM purchase_order
ORDER BY created_at DESC, id
LIMIT 100;
```

This is a diagnostic example, not the exercise solution. The note's SQL was not executed as a standalone example.

</details>

With hypothetical plan output `rows=1` estimated but `actual rows=5000`, investigate selectivity, skew and correlated predicates before forcing a join strategy. A nested loop can be excellent for a few outer rows with cheap indexed probes; repeated large inner scans can be costly. Hash joins can suit larger sets but consume memory. A sequential scan can be optimal when most rows are needed. [Plan interpretation](https://www.postgresql.org/docs/17/using-explain.html)

Run data-changing EXPLAIN statements only where their effects are acceptable: ANALYZE actually performs writes. A rollback can undo transactional writes, but not every external side effect or sequence advance.

## Index the access pattern

For a separate illustrative query, consider a customer's recent shipments. An index on `(customer_id, shipped_at DESC)` groups one customer's entries and orders their shipment times. Equality on the first column narrows the range before a time condition is applied. Reversing the columns changes the range scanned. An index on a trailing column alone is not equivalent. [PostgreSQL 17 multicolumn indexes](https://www.postgresql.org/docs/17/indexes-multicolumn.html)

| Symptom | Investigate | Tradeoff |
|---|---|---|
| Function or cast applied to indexed values | Keep compatible types; consider an expression index when necessary | Extra storage and write cost |
| Many irrelevant rows discarded before LIMIT | Align filtering and ordering with an access path | Index choice depends on workload |
| Correlated subquery repeated many times | Whether repeated work can be avoided | Preserve existence versus multiplicity semantics |
| Deep OFFSET pagination | A cursor using a deterministic ordering tuple | Different navigation contract |
| Few queries but huge payloads, or many tiny queries | Projection, bounded retrieval and batching | Bound application memory too |

Changing an existence check to a join can duplicate orders if multiple matches are possible. Aggregation before versus after a join can also change totals. Prove equivalence before measuring speed.

Partial indexes cover only qualifying rows and need predicates the planner can establish; prepared statement plans may affect eligibility. Covering indexes can reduce heap access, but index-only scans also depend on visibility information. Every additional index consumes space and increases write maintenance. [Partial indexes](https://www.postgresql.org/docs/17/indexes-partial.html), [index-only scans](https://www.postgresql.org/docs/17/indexes-index-only-scans.html)

## Check your understanding

1. Two products share a supplier code but have different current supplier emails. Is this fixed by adding an inventory row ID?

   <details>
   <summary>Show answer</summary>

   No. The identity of the product row does not enforce the dependency between supplier code and current contact details. Establish one authoritative supplier record and enforce the product's reference to it.

   </details>

2. Why keep a sale price on the order line instead of always joining the current catalogue price?

   <details>
   <summary>Show answer</summary>

   They describe different times and obligations. Changing a catalogue must not rewrite the price already agreed for an order. State the meaning of each fact before deciding that two values are redundant.

   </details>

3. A plan reports an inner operation taking 0.2 ms with 10,000 loops. Is the operation insignificant?

   <details>
   <summary>Show answer</summary>

   Its accumulated time is approximately 2000 ms. Repeated inexpensive work can dominate a request; inspect the outer row count and the inner access path. Do not add this time again to an inclusive parent time.

   </details>

4. A rewrite runs faster but returns duplicate orders. Can a DISTINCT make it acceptable?

   <details>
   <summary>Show answer</summary>

   First explain why the rewrite multiplied rows and establish the required semantics. DISTINCT might hide a wrong join, alter the projection's meaning or add a sort/hash cost. Validate rows, flags and ordering before accepting any speedup.

   </details>

5. Would you add five indexes to avoid every sequential scan?

   <details>
   <summary>Show answer</summary>

   No. Measure representative reads and writes. A scan can be appropriate for a small table or broad query. Add indexes for useful access patterns and account for storage, write throughput and deployment impact.

   </details>
