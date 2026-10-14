# Learning notes

Personal learning notes, organized into category folders as topics are added.

To capture a topic, send a prompt in this repository:

```text
/learn Docker networking
```

You can include what you learned, questions, examples, or source links:

```text
/learn Docker networking — add an explanation of bridge networks and container DNS
```

The workflow searches existing notes first and improves a matching note with new information. For a new topic, it prepares a preview and proposes a category and filename, then waits for your approval before creating the note. You can choose a different location. Each new note requires approval.

Categories are created with approved notes, for example `devops/docker-networking.md` or `programming/python-generators.md`. No category structure is imposed in advance.

The reusable workflow lives in [.agents/skills/learn/SKILL.md](.agents/skills/learn/SKILL.md), where Codex discovers the repository-local skill directly.

Invoke the installed skill with `$learn <topic>` or select it through `/skills` in Codex. If it does not appear, restart Codex. The `/learn` text remains a prompt convention handled by [AGENTS.md](AGENTS.md), rather than a registered slash-menu command.
