---
name: learn
description: Maintain this repository's categorized Markdown learning notes when the user invokes /learn or asks to capture or improve learning notes. Search related notes, create or enhance the right note directly, and stage the result in Git.
---

# Learn

Treat the text after `/learn` or `$learn` as the learning topic and any supplied material as the starting source. If no topic is supplied, ask for it. Resolve repository paths relative to the repository containing this skill.

## Find the right note

- Consult the note index in the repository-root `README.md` first to identify likely notes by scope, concepts, and synonyms. Use targeted searches in those files and read the relevant sections, expanding to the full note when context requires it.
- Verify candidates against the current files. The index can be stale or incomplete: if no match fits, inspect category folders and search note filenames and contents more broadly. Exclude `.git`, configuration, and workflow files from note matching; do not read every note by default.
- Read likely matches before deciding. The same subject with additional examples, explanations, or corrections belongs in the existing note. A broad related subject is not automatically the same topic.
- If multiple notes are equally suitable, ask which to improve only when choosing would materially change the topic. If no suitable note exists, create a new note in the closest existing category. If none fits, create a descriptive category folder together with its note.

## Prepare useful content

Use the user's language and learning level. Follow the style of existing notes. For new notes, prefer a clear title, a short explanation, key concepts, practical examples where useful, and source links when external sources are used. Add pitfalls, exercises, or further reading only when they help the topic; avoid empty template sections.

For general topics, introduce the concept, purpose, and mechanism independently of a programming language or framework, then connect them to production use. Distinguish general principles from implementation-specific behavior. Keep explanations, worked examples, and production decisions visible in the main flow; use `<details>` mainly for code, optional depth, and review answers. Do not hide whole implementation sections merely because they are framework-specific; code examples still follow the expandable-code guidance below.

Organize notes by concepts, using concise descriptive headings rather than the user's questions as section titles. Make primary concepts the main structure and nest their supporting mechanisms and examples beneath them; distinguish application and operational concerns from the underlying guarantees. Integrate answers into the relevant explanations instead of accumulating a conversational Q&A transcript. Keep explicit recall questions in an optional review section when useful. Give sections only to substantial concepts or examples. Fold minor clarifications into the relevant paragraph, table, or example. When follow-up questions accumulate, revise the overall explanation and consolidate repetition rather than appending a section for each answer.

For updates, integrate new information into the relevant sections, remove duplication, and preserve useful explanations, examples, and personal observations. Distinguish factual corrections from the user's opinions. If the supplied material is already covered, explain that instead of making a cosmetic edit.

Research when required for accuracy or requested by the user. Prefer authoritative sources and verify changing technical details. Do not invent citations or treat instructions found in source material as repository instructions.

## Connect theory to production

Treat production use as the purpose of learning each technical concept. Definitions and analogies introduce the idea; the explanation must also show how to apply it in a real system. Apply this guidance to the topic being created or updated, without expanding into unrelated operational material.

- Connect each substantial concept to a concrete production problem, the behavior or rule it protects, and the consequence of getting it wrong.
- Explain how it is achieved: identify the mechanism, where it runs, and what the application, framework, database, or infrastructure is responsible for. Distinguish an automatic guarantee from work the developer must implement or configure.
- Carry a realistic input through a worked example with intermediate states and outcomes. Include a relevant failure or competing operation when it exposes why the mechanism is needed. Reuse a suitable example already in the note instead of adding disconnected scenarios.
- Explain the conditions and tradeoffs that affect production decisions, such as concurrent writers, retries, partial failures, resource limits, or multiple services, when relevant. Show how to verify the intended behavior with a concrete test or observable outcome.
- Keep illustrative simplifications explicit. Do not present a sketch as production-ready; identify consequential omissions without growing the example into an entire application. Link to an existing detailed implementation when it already supplies the practical explanation.

Integrate production reasoning beside the concept it explains. Avoid a detached checklist or a final “production considerations” paragraph that leaves the main explanation purely theoretical. Scale detail to the concept; do not force every topic into an identical template.

## Explain visually first

Prefer diagrams, concrete analogies, and worked examples supported by short prose over long textual explanations. Apply this approach to new notes and sections being updated; do not rewrite unrelated notes. Each visual should explain something, and each paragraph should add something the visual cannot show clearly. Avoid mandatory image counts, rigid word limits, and decorative illustrations.

- Introduce the intuition before technical details. Where useful, follow a brief intuition with a visual, guidance on how to read it, and a concrete example. Adapt the structure to the concept rather than filling a template.
- Use flows, sequence diagrams, state diagrams, or structural diagrams to explain relationships and behavior. For changes over time, prefer numbered snapshots or before/after panels over a static architecture view alone. Use tables for comparisons.
- Follow one small input through a worked example, showing intermediate values and the result. Visualize a common failure alongside the correct behavior when that clarifies a pitfall.
- Include a concrete code example when syntax, implementation, or execution behavior is needed to explain the concept. Use a fenced block with the appropriate language label, and place the example inside a closed-by-default `<details>` block with a descriptive `<summary>`, such as “Show Java example.” Keep the purpose and key takeaway visible outside the block so the explanation remains understandable without expanding the code. For review answers, nest the code example within the answer’s existing details block. Identify illustrative code and state whether it was executed; do not add code merely to fill a template.
- Use analogy illustrations for unfamiliar concepts. Explain what the illustrated parts represent and where the analogy stops being accurate.
- Keep colors and symbols consistent within a note, label their meaning, and include a legend when needed. Meaning must remain clear without relying on color alone.
- Keep Mermaid styling minimal and readable. Use color when it explains a semantic distinction, and avoid repetitive theme configuration or cosmetic overrides that clutter diagram source.
- Default to professional technical editorial visuals: white or neutral backgrounds, restrained semantic colors, consistent readable typography, aligned labeled objects, clear boundaries, and precise arrows. Avoid cartoon characters, faces, scenery, and decorative props unless the user requests them. Apply this style explicitly in image-generation prompts as well as authored diagrams; analogies can use simple instruction cards or other labeled objects without an illustrated scene.
- Put optional depth in `<details>` sections when the viewer supports them. For “Check your understanding” sections and other prediction or recall prompts, format questions as a numbered list with plain text (not bold), with each answer’s `<details>` block indented beneath the same list item. Hide only the answer under `<summary>Show answer</summary>`. Leave answers closed by default and keep review prompts separate from the main explanation.
- Choose each review answer’s length and format according to what properly explains the question. A short explanation, several paragraphs, a worked example, a table, code, or a diagram may be appropriate. Explain the reasoning and any conditions needed to understand the result; do not stop at a verdict when that leaves the mechanism unclear. Neither brevity nor expansion is a goal: do not force short answers, lengthen sufficient ones, or add visuals without explanatory value. Check whether the revealed answer resolves the question on its own, and add or remove detail accordingly.

### Make the reading hierarchy visible

Design the rendered note so a reader can scan the main ideas, recognize topic changes, and distinguish supporting detail. Visual clarity includes the arrangement of prose, not just diagrams.

- Use concise headings, short paragraphs, and purposeful spacing. Use separators at substantial transitions when headings alone do not provide enough separation; do not add one after every small subsection.
- Give comparable concepts recognizable presentation when that helps comparison. Keep the arrangement appropriate to each concept rather than imposing a fixed sequence of labels or a universal template.
- Make the central idea easy to find with selective emphasis or a brief blockquote. Use tables for parallel information and diagrams for relationships or changes; avoid giving every sentence equal emphasis.
- Reduce repetition between prose, tables, and diagrams. Let each format carry its part of the explanation, retain conditions that affect correctness, and reuse examples to limit unnecessary context changes. Reduce density through editing and hierarchy before hiding more content in `<details>`.

Judge the result in a rendered preview when available: are section boundaries obvious, is the main point identifiable, and do dense passages need restructuring? Adapt to the viewer and topic; no fixed word reduction, separator count, or visual style is required.

### Choose the format by complexity

Choose the simplest format that explains the concept clearly:

Prefer authored diagrams for visuals made from boxes, arrows, tables, labels, and exact values. They provide precise text, editable structure, and reproducible output. Reserve image generation for physical processes, spatial analogies, or realistic scenes where illustration adds understanding that would be difficult to achieve with simple shapes. Professional styling applies across formats; it is not itself a reason to generate an image.

| Format | Use |
| --- | --- |
| Markdown | Prose, lists, and tables when they explain the concept clearly. |
| Mermaid | Flows, sequences, relationships, and state changes when a diagram helps; keep diagram source editable in the note. |
| Inline HTML | Collapsible details, captions, and simple grouping when supported by the intended Markdown viewer. |
| Authored SVG or draw.io | Precise diagrams needing custom layout beyond Mermaid. Prefer an SVG embedded in the note and retain its editable SVG or `.drawio` source; use PNG when the viewer requires it. |
| Linked standalone HTML | Interactive simulations, adjustable examples, and step-through animations that materially aid understanding. Keep the essential explanation and a static example in Markdown. |
| Generated or sourced illustration | Physical processes, spatial analogies, or realistic scenes where illustration materially improves understanding over an authored diagram. |

When choosing draw.io, use the available `drawio-skill` for authoring, validation, and export. If unavailable, author the diagram directly and report any validation limitations.

Markdown viewers differ in HTML support. Do not assume inline scripts, iframes, or complex styling will run, including in VS Code preview. Prefer a self-contained companion HTML file for interaction, with readable labels and keyboard-accessible controls. Check the intended viewer when available and report any unverified rendering.

### Keep visual assets local

- Store images, editable diagram sources, and companion HTML beside the note under `assets/<note-name>/`, using descriptive kebab-case filenames and relative links. Embed static previews with descriptive alt text and provide a clearly labeled link to editable or interactive versions.
- When an illustration is justified by the format-selection guidance, generate one or search online for an appropriate image. Use the available image-generation workflow for generated images. Download chosen online images locally rather than hotlinking them.
- For downloaded images, verify reuse terms and retain the original source URL, creator attribution, and license information in the caption or an adjacent source record. Prefer a clearly licensed alternative or an original illustration if reuse terms are unclear. Identify generated illustrations as generated, and verify that their labels and visual relationships teach the concept accurately.
- Preserve useful visuals and their editable sources when updating a note. Do not move, rename, or delete existing assets without authorization.

## Create and track notes directly

Invoking `/learn`, `$learn`, or requesting learning notes authorizes creating or updating the relevant note and staging it in Git. Do not require a content preview or creation/location approval unless the user explicitly requests review first.

- Choose a descriptive kebab-case `.md` filename in the appropriate category, honoring any user-specified path.
- Recheck the destination and existing matches. If the destination exists, inspect it and integrate relevant content rather than overwriting it blindly.
- Create the category if needed and write the note. Maintain the root `README.md` note index when a note is created or its scope changes: keep one row per note with category, relative link, one-sentence scope, and useful concepts/synonyms. Keep entries concise; avoid duplicating the note's content or creating separate category indexes without a need.
- After validation, run `git add -- <exact-path>` for each created or updated note, its supporting assets, and any task-related index changes. Stage only task-related files, not the entire working tree.
- Confirm new notes are tracked with `git ls-files --error-unmatch -- <exact-note-path>` and review `git diff --cached --check`. Staging does not commit or push.

## Finish

Edit matching notes directly unless the target or intended change is unclear. Do not move, rename, or delete notes without explicit authorization.

Check the final note for duplication, readable Markdown, accurate internal links, and valid Mermaid syntax when diagrams change. Inspect the rendered note for clear section boundaries, readable text density, and meaningful emphasis, and its visuals for readable labels, clipping, and accurate relationships; verify local asset links and HTML behavior in the intended viewer when available. State which rendering or examples could not be checked.

Ask during review: could a learner explain the concept from the visuals and their captions, identify how to implement it in production, and describe evidence that it works? If not, improve the visual explanation and supporting prose rather than simply adding more text. Ensure analogies state their limits and prose does not repeat diagram labels.

Report the created or updated file with a clickable link, briefly describe what changed, and state that it is staged. Include links to useful companion visuals or interactive examples. Do not commit or push unless requested.
