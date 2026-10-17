# Repository Guidelines

## Project Structure

This repository contains Markdown learning notes, not an executable application. Topic folders include `java/`, `software-design/`, and `production/`. `README.md` explains the learning workflow; `skills/learn/SKILL.md` defines it. Keep workflow instructions outside topic folders. There are no dedicated source, test, or asset directories.

## Development and Validation Commands

Run commands from `/workspace/learning`:

- `rg --files -g '*.md'`: list notes and documentation.
- `rg -n -i 'transaction' java software-design production`: find related material before adding content.
- `git diff --check`: detect whitespace errors in tracked changes.
- `git diff -- AGENTS.md`: review contributor-guide changes.
- `git status --short`: identify modified and untracked files.

No build, application server, test suite, or formatter is configured. Review newly created, untracked files directly; ordinary `git diff` does not show their contents.

## Markdown Style and Naming

Use descriptive kebab-case filenames, such as `java/java-agents.md`. Start each note with one `#` title and organize concepts under descriptive headings. Use concise explanations, tables for comparisons, and Mermaid for flows or concurrency. Introduce general concepts before framework-specific implementations. Label fenced code blocks with their language and identify illustrative code. Prefer relative links between notes and authoritative links for external claims.

## Content Verification

Preview Markdown, check relative links, and validate Mermaid syntax when changing diagrams. Verify technical claims against primary documentation when needed. Run examples when practical; state when examples were not executed. There is no coverage target or test-file naming convention.

## Learning Workflow

For `/learn`, `$learn`, or learning-note requests, read the repository's learn skill. Search existing notes and integrate matching material without duplication. Create new notes directly in the closest category, choosing a descriptive filename without requiring preview or location approval. Validate the result and stage the created or updated note with `git add <path>` so new files are tracked. Do not move, rename, or delete notes without authorization.

## Commits and Pull Requests

Local history uses short messages (`Initial commit`, `commit`); no strict convention is established. Prefer descriptive imperative subjects, such as `Clarify transaction isolation diagrams`. Keep commits focused. PRs should describe the learning change, list validation, and link relevant issues; include a rendered preview for significant visual changes.

Staging task-related files with `git add <path>` is allowed without additional approval. Do not commit or push unless requested. New files remain untracked until staged, and pushing only transfers commits. Never include secrets, `.env` files, or unrelated changes.
