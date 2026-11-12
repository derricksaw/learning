# Learning notes

Personal learning notes, organized into category folders, with runnable practice applications under `practice/`, starting with the [Spring Boot order service](practice/order-service/README.md).

The [Order Lab workspace](practice/order-service/README.md) includes a React frontend, two Spring API instances, PostgreSQL and Redis. See its [design document](practice/order-service/docs/design.md) and [runbook](practice/order-service/docs/runbook.md). From `practice/order-service/`, use `./scripts/init-env.sh` and `docker compose --profile app up -d --build --wait`, or the documented rootless runner. Open **http://localhost:5173**.


## Note index

Use this index to find likely matches, then search the relevant notes for detail. It is a discovery aid, not a substitute for checking the current files.

| Category | Note | Scope | Key concepts and synonyms |
|---|---|---|---|
| Java | [Java agents](java/java-agents.md) | Agent startup, bytecode instrumentation, and execution coverage. | JVM, premain, agentmain, transformer, JaCoCo, probes |
| Java | [Database transactions and Spring @Transactional](java/database-transactions-and-spring-transactional.md) | Database guarantees, concurrency, recovery, and Spring transaction boundaries. | ACID, MVCC, WAL, isolation, locking, rollback, propagation, outbox, JPA |
| Java | [Concurrency and thread pool design](java/concurrency-and-thread-pool-design.md) | Shared-state correctness, lock selection and CAS, executor capacity, priority scheduling, overload, and task lifecycle. | threads, CAS, mutex, read-write locks, optimistic locking, semaphores, contention, queues, priority, backpressure, deadlock, virtual threads |
| Production | [Production request lifecycle](production/production-request-lifecycle.md) | Request latency boundaries, telemetry, and investigation of slow requests. | logs, metrics, traces, spans, critical path, percentiles, p95, OpenTelemetry, OTLP, Jaeger, Tempo |
| Software design | [Separation of concerns, coupling, and cohesion](software-design/separation-of-concerns-coupling-and-cohesion.md) | Responsibility ownership, abstraction, dependencies, and boundaries that contain change. | information hiding, contracts, adapters, pricing, refactoring |
| Software design | [State machines and business workflows](software-design/state-machines-and-business-workflows.md) | Business states, submission eligibility, guarded concurrent transitions, and compensation. | state machine, guards, invariants, resubmission, optimistic locking, saga, orchestration, choreography |
| Software design | [Reliable asynchronous operations](software-design/reliable-asynchronous-operations.md) | Safe repetition, durable waiting, and recovery of external operations across failures. | unknown outcomes, retries, idempotency, reconciliation, operation identity, leases, generations, inbox, outbox |
| Software design | [Concurrency across application instances](software-design/distributed-concurrency.md) | Shared authority, version checks, durable idempotency, and temporary ownership across separate processes. | distributed concurrency, optimistic locking, stale writes, replicas, Redis, leases, fencing, idempotency, unique business keys |
| LeetCode | [146. LRU Cache](leetcode/146-lru-cache.md) | Cache recency, preserved Java attempts, and an efficient reference solution. | least recently used, eviction, HashMap, doubly linked list, LinkedHashMap |

## Turn learning into practice

New learning produces a focused exercise: Codex prepares the surrounding application and tests, you complete the topic-related gaps, then request a review. Reuse the [order service](practice/order-service/README.md) where it fits; additional applications and microservices are allowed when a topic needs them.

Start with [001 — Place orders without overselling](practice/order-service/requirements/001-place-orders.md). Track progress in the [requirements index](practice/order-service/requirements/README.md). The order service supplies the surrounding API and PostgreSQL infrastructure; the marked transaction and stock-reservation gaps are yours to complete.

## Capture a topic

To capture a topic, send a prompt in this repository:

```text
/learn Docker networking
```

You can include what you learned, questions, examples, or source links:

```text
/learn Docker networking — add an explanation of bridge networks and container DNS
```

The workflow searches existing notes first and improves a matching note with new information. For a new topic, it creates the note directly in an appropriate category with a descriptive filename. Include a preferred location or ask for a preview if desired. Each learning update prepares or refines the note, understanding questions, and a suitable focused exercise, then validates, commits, and pushes the learning changes. This also applies to follow-up note refinements without another `/learn`. Necessary starter code and requirements are included; learner code attempts, completed solutions, and review-only changes stay local unless you explicitly request publication. You can request local-only work or review before publication.

Categories are created as needed, for example `devops/docker-networking.md` or `programming/python-generators.md`. No category structure is imposed in advance.

The reusable workflow lives in [.agents/skills/learn/SKILL.md](.agents/skills/learn/SKILL.md), where Codex discovers the repository-local skill directly.

Invoke the installed skill with `$learn <topic>` or select it through `/skills` in Codex. If it does not appear, restart Codex. The `/learn` text remains a prompt convention handled by [AGENTS.md](AGENTS.md), rather than a registered slash-menu command.
