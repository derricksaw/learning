# Local operations runbook

Use either full Docker Compose or the rootless Linux runner from the [application README](../README.md). Both expose the console at `localhost:5173` and use APIs at 8080/8081, PostgreSQL at 5433, and Redis at 6380. Avoid running both stacks together.

## Rootless startup and recovery

```bash
./scripts/bootstrap-local.sh
./scripts/dev.sh up
./scripts/dev.sh status
```

The bootstrap uses Debian 13 packages to provide PostgreSQL 17 and Redis without root. It requires existing Java 21+, Node 22.12+, Python 3, Debian package metadata, and internet access for downloads. No system services or shell startup files are changed. PostgreSQL's socket directory, data, and generated database password are private workspace files under `.local/`.

The lifecycle runner initializes the database once, checks database existence on restart, starts Redis, packages the backend if it is stopped, waits for API A and API B to become healthy, and starts Vite. It stores each process PID with its Linux start-time signature so a stale PID file cannot stop an unrelated reused process. All child services run in separate process groups.

```bash
# Rebuild/restart after backend changes; stock and orders persist.
./scripts/dev.sh restart

# Stop only the owned local services; preserve data.
./scripts/dev.sh down
```

Logs are in `.local/state/api-a.log`, `api-b.log`, `frontend.log`, `redis.log`, and `postgres.log`. If startup fails, inspect the named log rather than deleting the database. Port conflicts must be resolved before restart. Interrupting startup can leave already-started dependencies running; `down` cleans up the owned services before retrying. Do not delete `.local/state/database-password` while retaining the existing database.

## Docker startup and recovery

```bash
./scripts/init-env.sh
docker compose --profile app up -d --build --wait
docker compose --profile app ps
docker compose --profile app logs -f --tail=80
```

`init-env.sh` creates an ignored, owner-readable `.env` only if absent. Compose injects its password into PostgreSQL and the APIs. Backend images use Java 21 and run under UID 10001. PostgreSQL/Redis readiness gates API startup, and API health gates frontend startup. Nginx provides the same API routing as Vite.

```bash
docker compose --profile app down
```

Normal stops retain the `orders-data` volume. Changing the password in `.env` does not reset the password inside an existing database volume. Keep the configuration with its volume, or explicitly rotate the database credential. Redis keys are transient; lock expiry is a recovery mechanism, not evidence of business completion.

Docker is not installed in the validated workspace and cannot be installed system-wide without administrator credentials. Compose files and image definitions are supplied; the rootless stack is the runtime validated here.

## Database changes and fixtures

Flyway applies schema migrations on startup. Do not edit an already-applied migration; add a later migration. The `dev` profile adds one-time catalog fixtures. Existing orders and quantities are never reset on restart. Default runs have no automatic reset endpoint.

The old manual SQL reset must include dependent invoice/document tables before truncating orders. For normal use, record inventory adjustments through the UI. A reset is destructive and should only target a disposable local database. Existing test fixtures use their own temporary PostgreSQL process and do not touch the running workspace.

## Exercise 005

Invoice insertion and both Redis ownership methods deliberately throw `501` until implemented. Startup and the rest of the workspace work with these gaps. The frontend reports the unfinished path and never fabricates an invoice. After saving an implementation, restart the APIs and request code review. No new test suite is required.

## Health and troubleshooting

| Symptom | Check |
| --- | --- |
| Console cannot connect | The selected API instance and its log; try the other instance to distinguish a process outage |
| APIs do not become healthy | PostgreSQL and Redis readiness, credentials, migrations and occupied ports |
| UI shows stale figures | Refresh; read projections poll every 10 seconds and are not a transaction boundary |
| Stale edit returns `409` | Expected behavior; reload and reconsider the desired correction |
| Invoice returns `501` | Exercise 005's TODOs are still open |
| Shipping returns `503` | Local instance's capacity is occupied; no carrier call was admitted |
| Missing document reports busy | Another lease owner may be rendering; inspect the durable result later |

This is a local-only application without authentication. Do not change loopback bindings to public interfaces without adding an appropriate access boundary.
