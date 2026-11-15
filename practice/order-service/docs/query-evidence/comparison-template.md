# Query comparison template

Copy this structure into the relevant exercise evidence file. All empty fields are learner-owned; this file contains no measured result or candidate solution.

## Contract and environment

- Exercise / request / result ordering and filters:
- PostgreSQL version / dataset counts and skew:
- Index definitions, statistics state, parameter values and page boundary:
- Cache state, load and generation/refresh time when relevant:

## V1 baseline

- Executable source method and full SQL snapshot:
- Original EXPLAIN output:
- Comments added next to V1 explaining rows, loops, buffers, sorting and the suspected bottleneck:
- Repeated timings and request/query-count observations:

## V2 candidate

- Hypothesis and precise change:
- Executable source method and full SQL snapshot:
- New index/migration or cache/materialization state:
- EXPLAIN output and repeated timings:

## Comparison and decision

| Observation | V1 | V2 | Interpretation / limitation |
|---|---|---|---|
| Result equivalence / intended contract change | | | |
| Execution time distribution | | | |
| Rows / loops / buffer work | | | |
| Query count / round trips / application memory | | | |
| Storage / write / refresh / fill cost | | | |
| Freshness / concurrent writes / failure recovery | | | |

State what changed, why the evidence supports it and what remains untested. SQL plans do not measure a Redis hit or the full HTTP request; record those separately. Record pre-index V1 evidence before adding shared indexes, because indexes can change both query versions.
