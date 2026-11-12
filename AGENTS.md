# Repository Guidelines

## Project Structure

This repository contains Markdown learning notes and practice applications under `practice/`, starting with the Spring Boot application at `practice/order-service/`. Topic folders include `java/`, `software-design/`, and `production/`. `README.md` explains the learning workflow; [.agents/skills/learn/SKILL.md](.agents/skills/learn/SKILL.md) defines it. The application has its own source, test, and requirements directories and shares this repository’s Git history. Keep workflow instructions outside topic folders.

## Development and Validation Commands

Run note-maintenance commands from the repository root:

- `rg --files -g '*.md'`: list notes and documentation.
- `rg -n -i 'transaction' java software-design production`: find related material before adding content.
- `git diff --check`: detect whitespace errors in tracked changes.
- `git diff -- AGENTS.md`: review contributor-guide changes.
- `git status --short`: identify modified and untracked files.

For the application, use `scripts/dev.sh` or the documented Compose setup in `practice/order-service/`; read its local `AGENTS.md` first. Run `./mvnw verify` for existing backend checks and `npm --prefix frontend run build` for the frontend. New exercises use code review by default; add exercise tests only when requested. Notes have no build or formatter. Review newly created, untracked files directly; ordinary `git diff` does not show their contents.

## Markdown Style and Naming

Use descriptive kebab-case filenames, such as `java/java-agents.md`. Start each note with one `#` title and organize concepts under descriptive headings. Use concise explanations, tables for comparisons, and Mermaid for flows or concurrency. Introduce general concepts before framework-specific implementations. Label fenced code blocks with their language and identify illustrative code. Prefer relative links between notes and authoritative links for external claims.

## Content Verification

Preview Markdown, check relative links, and validate Mermaid syntax when changing diagrams. Verify technical claims against primary documentation when needed. Run examples when practical; state when examples were not executed. There is no coverage target or test-file naming convention.

## Learning Workflow

For `/learn`, `$learn`, or learning-note requests, read the repository's learn skill. Search existing notes and integrate matching material without duplication. Create new notes directly in the closest category, choosing a descriptive filename without requiring preview or location approval. Prepare or refine understanding questions and a suitable focused exercise, validate the learning changes, then commit and push each note creation or refinement, including follow-up refinements. Learner code completion and review-only changes remain local unless explicitly requested. Do not move, rename, or delete notes without authorization.

## Practice workflow

Always provide the learning notes to the learner first, then proceed to practice preparation. Put exercises inside the working application, using real features and deliberately incomplete or inefficient implementations where appropriate; do not create isolated learning labs.

New technical learning should produce or refine a focused exercise in a suitable practice application. Build supporting application code, setup, and fixtures so the learner can work directly on a small set of topic-related gaps. Explain unfamiliar framework syntax directly; guide and review the target problem without filling its solution unless asked. Additional applications or services are allowed when the topic requires separate boundaries, including microservices. Share the parent Git history and inspect existing learner work before editing.

## Commits and Pull Requests

Local history uses short messages (`Initial commit`, `commit`); no strict convention is established. Prefer descriptive imperative subjects, such as `Clarify transaction isolation diagrams`. Keep commits focused. PRs should describe the learning change, list validation, and link relevant issues; include a rendered preview for significant visual changes.

Staging task-related files with `git add <path>` is allowed without additional approval. Learning-note creation/refinement is standing authorization to commit and push its notes, questions, requirements, assets, and necessary exercise scaffold; inspect staged and outgoing changes to exclude learner solutions and unrelated work. Code completion, review cleanup, and completion-status updates alone do not authorize commit/push. Other work requires an explicit request. New files remain untracked until staged, and pushing only transfers commits. Never include secrets, `.env` files, or unrelated changes.
