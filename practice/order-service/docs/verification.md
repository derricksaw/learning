# Verification record

Validated on 12 November 2026 in the local workspace. Exercise 005's three target implementations remain intentionally unfinished.

| Area | Result |
| --- | --- |
| Backend build and existing regression suite | `./mvnw -B verify` passed: 28 tests, zero failures/errors |
| Frontend | TypeScript check and Vite production build passed |
| Rootless dependency installation | PostgreSQL 17.11 and Redis 8.0.2 downloaded/extracted without administrator access |
| Full local runtime | PostgreSQL, Redis, two independent API JVMs and Vite started successfully |
| Runtime lifecycle | Stop/restart completed; database contents survived; both instances returned healthy |
| Docker Compose | The complete `app` profile passed `config --quiet` validation with the standalone Compose client |
| Docker images | Not built or run here: no Docker daemon or administrator credentials are available |
| Scripts | Shell syntax and Python compilation checks passed |
| Documentation | Relative links and the design document's Mermaid diagrams checked |

Browser verification used installed Chromium through Playwright against the real running frontend, APIs and shared database. It exercised order creation, stock adjustment, a stale-version save through the second instance, a simulated shipping label, the expected invoice `501`, and API instance selection. No browser page errors were observed. Desktop and 390px-wide mobile layouts were checked; document-level mobile horizontal overflow was fixed. Tables can scroll inside their own containers.

[Desktop capture](assets/workspace-desktop.png) and [mobile capture](assets/workspace-mobile.png) show actual local development state. The temporary browser verification script was not added as an exercise test.

This verifies supporting setup and existing behavior, not the unfinished invoice/Redis TODOs, a production deployment, failover safety, or high-load performance. No new automated exercise tests were added.

## Redis compatibility upgrade — 15 November 2026

Compose and the rootless runner now select Redis 8.4.8. The rootless release was built from the upstream archive after validating its SHA-256 against Redis's published checksum. An isolated server verified that `DELEX ... IFEQ ...` deletes only the matching token, leaves a different token intact, returns zero for a missing key, and cannot delete a newer lease after expiry and reacquisition. Compose configuration and shell/Python syntax checks passed. The workspace was stopped before this change and remains stopped; only the isolated verification server was started and shut down. No exercise tests were added or rerun, and learner Java implementations were preserved.
