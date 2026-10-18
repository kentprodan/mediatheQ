# 0009. Project rules live in Claude Code skills

- Status: Accepted
- Date: 2026-10-18

## Context

The app is built by vibecoding with Claude Code. In the old build, rules lived in issues and chat history, and were applied inconsistently (e.g. Expressive only partly used).

## Decision

- Project rules live in `.claude/skills/<name>/SKILL.md`, committed to git: `expressive-design`, `architecture`, `testing`, `jellyfin` (and later `media3`, `tv`, ...).
- Skills are short, concrete and checkable; each ends with a done checklist used by `/end`.
- When code and a skill disagree, fix one of them in the same PR. When a decision changes, write an ADR first, then update the skill.
- Official Android skills (`android/skills`) may be installed as a plugin and complement, not replace, the project skills.

## Consequences

- Skills must be kept current; an outdated skill is a bug.
- New contributors (human or AI, e.g. Copilot) get the same rules from the repository.
