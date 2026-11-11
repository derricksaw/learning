# Practice application guidance

This application supports focused exercises. Codex builds the surrounding system; the learner completes the topic-related gaps.

- Read `README.md` and `requirements/README.md` before preparing an exercise or reviewing an attempt.
- Implement supporting endpoints, DTOs, configuration, repositories, fixtures, and frontend flows. Use code review for new exercises by default; add exercise tests only when the user requests them. Preserve and extend the learner's existing work. Leave explicitly marked target gaps unresolved unless the learner asks for their solution.
- Explain framework annotations, APIs, and syntax directly. Guide the learner's decisions about the target concept with hints, observations, and review.
- Document supplied pieces, target files, acceptance criteria, and verification commands. Keep support checks distinct from exercise checks; expected exercise failures must remain visible.
- For iterative learner reviews, inspect saved code directly when requested; do not rerun the suite automatically. Check supporting changes with their focused tests. `LockingExerciseTests` covers 002, and `TransactionExerciseTests` covers 001. The full `./mvnw verify` includes all exercises, including unfinished ones.
- After accepting an implementation in review, remove its obsolete exercise markers and unused imports yourself. Record whether the review was by inspection or backed by runtime checks; do not mark unverified acceptance criteria complete.
- Additional practice services are allowed when a topic needs a service boundary. Share the parent Git repository and keep generated build output untracked.

## Full-stack workspace

- `frontend/` contains React/TypeScript/Vite. Run `npm --prefix frontend run build` after frontend changes. Keep the API client typed and preserve server errors; do not fabricate success for unfinished exercise endpoints.
- `docs/design.md`, `docs/api.md`, and `docs/runbook.md` describe the architecture, contract, and operating setup. Update them when boundaries or setup change.
- `scripts/dev.sh` controls only owned rootless processes; `compose.yaml` adds full application services under the `app` profile. Keep both API route prefixes working.
- Preserve inline exercise TODOs and learner work during infrastructure/UI changes. Do not solve exercise 005 as part of ordinary setup.
- Never stage `.local/`, `.env`, dependency directories, credentials, generated builds, or logs. Run existing checks as appropriate for supporting changes; no new exercise tests are required.
