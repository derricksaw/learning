# Database transactions and Spring @Transactional

**A transaction accepts related database changes together, or discards them together.** Start with the database concepts below; Spring appears in section 7.

SQL behavior is illustrated with PostgreSQL. Diagrams are original explanations; linked official documentation provides further detail.

## 1. One operation, several changes

Alice transfers RM100 to Bob. The rule (**invariant**) is that their combined RM700 stays RM700.

```mermaid
flowchart LR
    A["Before<br/>Alice RM500 · Bob RM200"] --> T
    subgraph T[One database transaction]
        D[Deduct Alice RM100] --> C[Credit Bob RM100]
    end
    T --> Q{Outcome}
    Q -->|Commit| OK["Alice RM400 · Bob RM300"]
    Q -->|Rollback| OLD["Alice RM500 · Bob RM200"]
    style T fill:#eef6ff,stroke:#2563eb
    style OK fill:#ecfdf5,stroke:#059669
    style OLD fill:#fff7ed,stroke:#ea580c
```

Without a shared transaction, a crash after the deduction can leave Alice RM400 and Bob RM200.

| ACID property | What it contributes |
|---|---|
| **Atomicity** | Both transactional changes commit, or neither does |
| **Consistency** | Constraints and correct logic preserve the rule |
| **Isolation** | Defines how overlapping operations interact |
| **Durability** | Accepted changes survive failures under the configured guarantees |

**Choose the boundary around the complete rule.** Two independent connections are not automatically one transaction. Database rollback cannot reverse a completed external charge.

## 2. Commit, rollback, and savepoints

```mermaid
flowchart LR
    B[BEGIN] --> W[Execute changes]
    W --> D{Can complete?}
    D -->|Yes| C[COMMIT]
    D -->|No| R[ROLLBACK]
    C --> P[Changes accepted]
    R --> X[Pending changes discarded]
```

```sql
BEGIN;
UPDATE account SET balance = balance - 100.00 WHERE id = 1;
UPDATE account SET balance = balance + 100.00 WHERE id = 2;
COMMIT; -- Choose ROLLBACK instead if the operation cannot complete.
```

This sketch groups writes; real transfer logic must also validate accounts, funds, concurrency, and duplicate requests. **Statement success is not transaction commit.** With autocommit, individual statements can commit separately.

```mermaid
flowchart LR
    A[Insert draft order] --> S[SAVEPOINT]
    S --> B[Mark reviewed]
    B --> R[ROLLBACK TO savepoint]
    R --> C[COMMIT draft order]
```

A savepoint discards later work while retaining earlier pending work. It is not an independent commit. After commit, reversing an operation requires another operation. [Database transaction tutorial](https://www.postgresql.org/docs/current/tutorial-transactions.html)

## 3. What happens inside the database

### Snapshots: which version can I read?

**MVCC** keeps row versions so a reader can see an appropriate snapshot while a writer changes data.

```mermaid
sequenceDiagram
    participant A as Reader
    participant DB as Row versions
    participant B as Writer
    A->>DB: Read balance
    DB-->>A: 500
    B->>DB: Write 400 and commit
    A->>DB: Read again
    DB-->>A: Read Committed returns 400 / Repeatable Read returns 500
```

This PostgreSQL example assumes the reader stays in one transaction and makes no writes. Writers still contend; long snapshots can delay cleanup. [MVCC](https://www.postgresql.org/docs/current/mvcc-intro.html)

### Logging: how does committed data survive?

```mermaid
flowchart LR
    W[Change data] --> L[Write recovery log]
    L --> F[Make required log durable]
    F --> A[Acknowledge commit]
    L --> P[Write data pages later]
    F -. Crash recovery .-> R[Replay durable committed work]
```

**Write-ahead logging (WAL)** allows recovery without forcing every changed data page to disk at commit. The diagram assumes normal durable commit settings; asynchronous settings weaken crash guarantees. Replication durability is a separate choice. [WAL](https://www.postgresql.org/docs/current/wal-intro.html)

## 4. Concurrency: a transaction can still make a wrong decision

### Two buyers, one item

```mermaid
sequenceDiagram
    participant A as Buyer A
    participant DB as Stock = 1
    participant B as Buyer B
    A->>DB: Read stock
    DB-->>A: 1
    B->>DB: Read stock
    DB-->>B: 1
    A->>DB: Set stock to 0
    A->>DB: Insert order A
    A->>DB: Commit
    B->>DB: Set stock to 0
    B->>DB: Insert order B
    B->>DB: Commit
    Note over A,B: Two committed orders for one item
```

Both transactions were atomic. Their read-check-write decisions were unprotected.

**Fix for this single-row rule:** express the check and deduction in one conditional update.

```sql
UPDATE inventory
SET available = available - :quantity
WHERE product_id = :id AND available >= :quantity;
```

Validate positive quantity and require exactly one affected row. In PostgreSQL Read Committed, the second competing update waits, then reevaluates its condition; after the first buyer commits, it cannot deduct the last item again.

| Isolation in PostgreSQL | What reads see | Important limit |
|---|---|---|
| Read Committed | A fresh snapshot per statement | Successive reads can differ |
| Repeatable Read | A stable transaction snapshot | Cross-row write skew remains possible |
| Serializable | Committed results match some serial order | Conflicts can abort; retry whole transaction |

PostgreSQL maps Read Uncommitted to Read Committed and prevents phantoms at Repeatable Read. Other databases differ. [Isolation behavior](https://www.postgresql.org/docs/current/transaction-iso.html)

### Write skew: different rows, one shared rule

```mermaid
flowchart TD
    S[Two doctors on call] --> A[A sees B available]
    S --> B[B sees A available]
    A --> A1[A goes off call]
    B --> B1[B goes off call]
    A1 --> X[Both commit: nobody on call]
    B1 --> X
    X --> F[Coordinate on a shared schedule lock<br/>or use Serializable with retries]
```

Locking only each doctor's own row does not protect “at least one stays.”

## 5. Choose a concurrency tool

| Tool | Mechanism | Good fit |
|---|---|---|
| Conditional update | Check and change together | Stock and counters |
| Pessimistic lock | Lock before deciding | Coordinated updates to known rows |
| Optimistic version | Reject a write based on stale data | Edits with tolerable conflicts |
| Constraint | Database rejects invalid stored state | Unique request IDs, nonnegative stock |
| Serializable | Detect incompatible concurrent decisions | Rules across rows or predicates |

### Lock before deciding

```sql
BEGIN;
SELECT balance FROM account WHERE id = 1 FOR UPDATE;
SELECT balance FROM account WHERE id = 2 FOR UPDATE;
-- Verify both exist and funds suffice; otherwise ROLLBACK.
UPDATE account SET balance = balance - 100.00 WHERE id = 1;
UPDATE account SET balance = balance + 100.00 WHERE id = 2;
COMMIT;
```

Lock both accounts in ascending ID order, including reverse transfers. Locks last through transaction completion; snapshot readers can still read older versions.

```mermaid
flowchart LR
    A[T1 holds account 1] -->|Waits for| B[Account 2 held by T2]
    B -->|T2 waits for| A
```

That cycle is a **deadlock**. Consistent lock order reduces it; if the database aborts a participant, retry the complete operation. Keep remote calls outside held locks. [Locking and deadlocks](https://www.postgresql.org/docs/current/explicit-locking.html)

### Check the version before accepting

```sql
UPDATE inventory
SET available = :newValue, version = version + 1
WHERE product_id = :id AND version = :versionRead;
```

```mermaid
flowchart LR
    R[Read version 8] --> D[Make decision]
    D --> W{Write only if still version 8}
    W -->|1 row| OK[Accepted: version 9]
    W -->|0 rows| X[Reload and reconsider]
```

All writers must follow the version protocol. Updating the expected version without reconsidering the decision is unsafe. Per-row versions do not automatically protect cross-row rules.

## 6. Failures, retries, and external systems

```mermaid
flowchart TD
    F[Request failed] --> Q{Known outcome?}
    Q -->|Confirmed abort| T{Temporary conflict?}
    T -->|Yes| R[Bounded backoff outside transaction]
    R --> N[Fresh transaction: reload and reevaluate]
    T -->|No| E[Correct input or report failure]
    Q -->|Commit response lost| U[Query using original operation ID]
    U --> C{Recorded result?}
    C -->|Yes| O[Return original result]
    C -->|Unresolved| I[Reconcile before repeating effects]
```

A timeout does not prove rollback. Use a stable request ID plus a unique constraint; replay the stored result only when the payload matches. PostgreSQL `40001` identifies serialization failure and `40P01` deadlock. Retry known aborts, not arbitrary connection failures. [Error codes](https://www.postgresql.org/docs/current/errcodes-appendix.html)

### Commit the intent; deliver later

Sending before commit can announce rolled-back data. Sending after commit can be skipped by a crash. An **outbox** makes outgoing work durable alongside the business change:

```mermaid
flowchart LR
    subgraph TX[One local transaction]
        O[Save order] --> E[Save outbox event]
    end
    TX --> C[COMMIT]
    C --> W[Worker reads durable event]
    W --> S[Send to warehouse]
    S --> M[Mark delivered]
    S -. Crash before marking .-> W
```

Delivery can repeat: receivers need duplicate-safe effects. Workers need claiming and crash recovery. A refund compensates for a completed charge; it does not erase it. For detailed payment scenarios, see [workflow recovery](../software-design/state-machines-and-business-workflows.md).

## 7. Spring applies these database concepts

**`@Transactional` declares a boundary.** Spring coordinates it; the database enforces the underlying guarantees.

```mermaid
sequenceDiagram
    participant C as Caller
    participant P as Spring proxy
    participant M as Transaction manager
    participant S as Service
    participant DB as Database
    C->>P: Call transactional method
    P->>M: Start or join transaction
    M->>DB: Begin if needed
    P->>S: Execute method
    S->>DB: Participating database operations
    S-->>P: Return or throw
    P->>M: Complete according to rules and status
    M->>DB: Commit or rollback at owning boundary
    P-->>C: Result or failure
```

The target method returning is earlier than commit completion. Joined methods do not each commit independently. Imperative transactions are thread-bound; reactive transactions use Reactor context. [Spring implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)

| Resource | Typical manager |
|---|---|
| JDBC DataSource | `JdbcTransactionManager` |
| JPA EntityManagerFactory | `JpaTransactionManager` |
| Compatible enlisted XA resources | `JtaTransactionManager` |
| R2DBC | Reactive transaction manager |

For local JDBC, the manager binds a connection to the transaction; participating `JdbcTemplate` calls reuse it. At completion it commits/rolls back, restores state, and releases the resource. Opening an unrelated connection can escape the boundary. [Resource synchronization](https://docs.spring.io/spring-framework/reference/data-access/transaction/tx-resource-synchronization.html)

### The proxy must intercept the call

```mermaid
flowchart LR
    C[Caller] --> P[Spring proxy]
    P -->|Advice runs| A[Service method A]
    A -->|Direct self-call: advice bypassed| B[Annotated method B]
```

Use injected Spring beans and public boundary methods. `new Service()` and self-invocation bypass advice; class-based proxies cannot override final methods. A self-called method can still use an existing transaction, but its annotation settings are not intercepted. [Annotation behavior](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 8. Propagation and rollback

```mermaid
flowchart TB
    subgraph REQUIRED[REQUIRED: one physical transaction]
        A[Outer scope] --> B[Inner scope shares T1]
    end
    subgraph NEW[REQUIRES_NEW: independent transactions]
        C[Outer T1 suspended] --> D[Inner T2 completes]
        D --> E[Outer T1 resumes]
    end
    subgraph NESTED[NESTED: savepoint within T1]
        F[Outer work] --> G[Inner work at savepoint]
        G --> H[Partial rollback possible; outer owns final commit]
    end
```

| Mode | Typical use | Important cost or limit |
|---|---|---|
| `REQUIRED` — default | One business unit across several calls | Inner failure can invalidate shared work |
| `REQUIRES_NEW` | Independently durable attempt/audit record | Extra connection; outer locks remain held |
| `NESTED` | Partial rollback with savepoints | Resource support required; not portable JPA nesting |
| `SUPPORTS` / `MANDATORY` | Participate optionally / require existing work | `MANDATORY` fails without a transaction |
| `NOT_SUPPORTED` / `NEVER` | Suspend / forbid transactional execution | `NEVER` fails if a transaction exists |

Joined scopes normally inherit the outer isolation and timeout. Independent audit work must not depend on an uncommitted outer order. [Official Spring propagation diagrams and semantics](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

### Why catching an exception may still roll back

```mermaid
flowchart LR
    A[Outer starts T1] --> B[Inner proxied REQUIRED call fails]
    B --> C[Shared T1 marked rollback-only]
    C --> D[Outer catches and returns]
    D --> E[Completion rolls back]
    E --> F[UnexpectedRollbackException]
```

Default rules roll back on `RuntimeException` and `Error`, not ordinary checked exceptions. Configure a checked failure explicitly:

```java
@Transactional(rollbackFor = IOException.class)
public void importFile() throws IOException { /* database work */ }
```

A locally caught application exception may allow commit if the transaction remains usable. Catching cannot clear an existing rollback-only/aborted state. Database rollback also does not rewind Java objects or external effects. [Rollback rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)

## 9. Example: order, stock, and outbox

```mermaid
flowchart LR
    subgraph T[One transaction]
        A[Insert unique request and order] --> B[Conditionally deduct stock]
        B --> C[Insert outbox event]
    end
    C --> D[Commit all three]
    B -->|No stock: throw| R[Roll back all three]
    C -->|Insert fails| R
```

The request uniqueness check happens before stock deduction. The example rejects duplicates; replaying the original response requires handling that failure outside the failed transaction, then reading and validating the committed result.

<details>
<summary>Expand implementation: configuration, PostgreSQL tables, and Spring JDBC service</summary>

Assumes Java text blocks, a configured `DataSource`, and one shared JDBC transaction manager. Spring Boot may configure the infrastructure automatically. This teaching example omits pricing, authorization, and full order lines.

```java
@Configuration
@EnableTransactionManagement
class TransactionConfig {
    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

```sql
CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY,
    available INTEGER NOT NULL CHECK (available >= 0)
);

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    product_id BIGINT NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status TEXT NOT NULL CHECK (status IN ('PLACED', 'CANCELLED'))
);

CREATE TABLE outbox (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES purchase_order(id),
    event_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
```

```java
@Service
public class OrderService {
    private final JdbcTemplate jdbc;

    public OrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(timeout = 5)
    public UUID placeOrder(UUID requestId, long productId, int quantity) {
        Objects.requireNonNull(requestId, "requestId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        UUID orderId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO purchase_order
                (id, request_id, product_id, quantity, status)
            VALUES (?, ?, ?, ?, 'PLACED')
            """, orderId, requestId, productId, quantity);

        int changed = jdbc.update("""
            UPDATE inventory
            SET available = available - ?
            WHERE product_id = ? AND available >= ?
            """, quantity, productId, quantity);

        if (changed != 1) {
            throw new OutOfStockException(productId);
        }

        jdbc.update("""
            INSERT INTO outbox (event_id, order_id, event_type)
            VALUES (?, ?, 'OrderPlaced')
            """, UUID.randomUUID(), orderId);

        return orderId;
    }
}

class OutOfStockException extends RuntimeException {
    OutOfStockException(long productId) {
        super("No sufficient stock for product " + productId);
    }
}
```

Imports: Spring configuration/context and transaction annotations, `PlatformTransactionManager`, `JdbcTransactionManager`, `JdbcTemplate`, `Service`, `javax.sql.DataSource`, `java.util.UUID`, and `java.util.Objects`.

Name the request-ID constraint explicitly in production to identify its violation reliably. Match duplicate-request payloads; do not classify every integrity violation as a replay. Rejected attempts are not retained by this schema. The outbox worker still needs claiming, retries, and idempotent delivery.

</details>

## 10. JPA, operational limits, and verification

```mermaid
flowchart LR
    E[Change managed entity] --> F[Flush: execute SQL]
    F --> C[Commit: accept transaction]
    F --> R[Later failure: rollback]
```

`save()` and `flush()` do not imply commit. `@Version` implements optimistic conflict checks; after a conflict, reload and reconsider in a fresh transaction. Savepoint rollback does not rewind a JPA persistence context. [JPA specification](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html), [JPA manager limitations](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html)

| Situation | Practice |
|---|---|
| Several repository writes | Put one boundary around the business unit |
| Read-only work | `readOnly` is an optimization hint, not a portable write prohibition or snapshot guarantee |
| Long HTTP call | Release database locks before remote waiting |
| Async execution | Worker enters its own boundary; parent transaction is not inherited |
| Timeout | Resource-supported limit; does not interrupt arbitrary Java work |
| Large import | Choose whole-file atomicity or explicit resumable chunks; flush alone does not commit |
| Two databases | Two local managers do not make one atomic commit |
| After-commit event | In-process listener alone is not durable delivery |

See [repository defaults and read-only behavior](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), [transaction attributes](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html), and [transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html).

<details>
<summary>Expand retry boundary and verification cases</summary>

Each retry must enter a fresh transaction. From a **nontransactional coordinator**, use a configured `TransactionTemplate`:

```java
for (int attempt = 1; ; attempt++) {
    try {
        return tx.execute(status -> orderAttempt.execute(command));
    } catch (RuntimeException failure) {
        if (!isRetryableDatabaseAbort(failure) || attempt >= maxAttempts) {
            throw failure;
        }
        backoffWithJitter(attempt); // Outside the completed transaction.
    }
}
```

Helpers are application-specific. Classify known-aborted conflicts, bound elapsed time too, and avoid external effects in the retried callback. Default `REQUIRED` would join an existing outer transaction, defeating fresh attempts. Catch outside `execute` to include commit failures. [Programmatic transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)

| Verify using the real database engine | Expected evidence |
|---|---|
| Failure after stock deduction | No order/event; stock unchanged |
| Two buyers, one item | One success; stock zero |
| Concurrent duplicate request | One order and deduction |
| Inner shared scope fails | Outer cannot silently commit |
| Worker crashes after delivery | Duplicate delivery produces no duplicate effect |

Call the injected proxy. Test-level transactions can mask missing boundaries and defer commit errors. Assert from a fresh transaction; use separate connections for concurrent actors and bounded waits. Avoid barriers that require a blocked actor to pass a held lock. [Transaction testing](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)

</details>
