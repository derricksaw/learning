---
name: exercise
description: Prepare focused implementation exercises with inline TODOs in a repository practice application. Use for /exercise, $exercise, or an explicit request for a TODO exercise. Infer the topic from the current conversation when no topic is supplied.
---

# Exercise

Use the topic, note, example, or code explicitly supplied with `/exercise` or `$exercise` when present. Otherwise, use the current conversation to identify the active learning topic and the concept the learner should practice. A note just discussed or created with `/learn` is sufficient when it establishes a clear focus.

If the conversation gives no actionable topic or leaves multiple plausible topics, ask one concise question for the topic, note, or example to use. Wait for the answer before preparing an exercise.

## Prepare the practice application

Discover practice applications and requirements from the repository-root `README.md`. Read the selected application's local guidance before editing. Use English for the requirements and learner instructions.

- Inspect existing requirements and the learner's work first. Reuse suitable applications; add another application or service when a topic needs a distinct runtime or service boundary, such as a microservice interaction. Keep related projects in this repository's Git history rather than creating nested repositories.
- Build the supporting pieces needed to reach the topic quickly: configuration, endpoints, data models, storage setup, fixtures, and wiring. Preserve useful learner work and adapt around it. Do not make unrelated boilerplate the learner's prerequisite.
- Leave a small, clearly marked set of unresolved decisions or implementation points directly related to the topic. Identify their files, expected behavior, and acceptance criteria without supplying the target solution. Prefer code that compiles and an application that starts; make incomplete paths explicit rather than silently reporting success or using a fake implementation.
- Always place a `TODO` comment directly at every pending exercise implementation site, inside the method, block, or configuration the learner must complete. Include the exercise ID and a concise description of the required behavior without giving the solution. A requirements document or an `EXERCISE` marker alone does not replace this inline TODO. Remove the TODO after the implementation is accepted in review.
- Use code review as the default exercise verification: inspect the saved implementation, trace normal and competing operations, and explain how its mechanism preserves the required behavior. Do not create automated tests for every exercise or rerun suites on each review. Add exercise tests only when the user requests them. Preserve existing tests and use relevant existing checks when validating supporting application changes; optional manual walkthroughs can illustrate runtime behavior. Record whether evidence comes from inspection or execution, and do not claim unobserved runtime results.
- Explain unfamiliar framework APIs, annotations, tooling, and syntax directly. Use hints and questions for the learning problem and review the learner's implementation; give the target solution only when explicitly requested. A review should identify behavior, mechanism, and verification, not just whether code compiles.
- Give each exercise a stable ID, link its learning note when one exists, and record scope, provided pieces, learner-owned gaps, dependencies, and status in the relevant requirement index. Refine matching exercises instead of duplicating them, and mark completion only when implementation and evidence support it.
- Keep exercise preparation and review changes local unless the user explicitly requests a commit or push. When publishing is authorized, include the starter code and requirements so the exercise can be used from a checkout. Exclude learner solutions and unrelated changes.
- Link the exercise requirement and point the learner to the first TODO. Report the checks performed and any verification gaps.

## Before finishing (exit checklist)

Confirm every item before handing the exercise back:

1. **Learner work preserved:** No existing learner solutions or unrelated local modifications were overwritten.
2. **Explicit TODOs, no leaks:** Every learner-owned gap has an inline `TODO` with the exercise ID and expected behavior, without revealing the target solution; incomplete paths fail explicitly rather than faking success.
3. **Compiles and verified:** Supporting code builds cleanly and relevant existing checks (`./mvnw verify`, plus `npm --prefix frontend run build` if frontend files changed) were executed, with exact results or blockers reported.
4. **Indexed and local-only:** The requirement links the learning note, is recorded in `requirements/README.md`, and all exercise edits remain local (no `git commit` or `git push`).
