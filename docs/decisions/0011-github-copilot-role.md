# 0011. Role of GitHub Copilot

- Status: Accepted
- Date: 2026-10-18

## Context

The owner has Copilot Pro. Claude Code builds the app issue by issue (ADR 0009). Copilot can add a second opinion on pull requests and take over small, well-defined chores, but two agents editing the same code at once cause conflicts, and premium requests are limited.

## Decision

- **Copilot code review** runs on every pull request as a second opinion. Its comments are weighed like any review; they do not override the project skills or ADRs.
- **Copilot coding agent** only takes issues labeled `copilot`. Such issues must be:
  - small and fully specified (acceptance criteria and required tests written out),
  - in code already covered by tests,
  - free of new architecture (no new module, no new module dependency, no ADR needed) and of new UI.
- Copilot and Claude Code never work on the same module at the same time. An issue labeled `copilot` names the modules it touches; Claude Code does not start an issue touching those modules until the Copilot PR is merged or closed.
- Copilot reads the same rules as Claude Code through `.github/copilot-instructions.md`, which points to `.claude/skills/*/SKILL.md` and `docs/decisions/`.
- Premium request usage is reviewed on **2026-11-01** (two weeks after this ADR); the result is recorded here or in a follow-up ADR.

## Consequences

- One more review voice on every PR at no extra effort.
- `copilot` issues need more upfront writing, which keeps them safe to delegate.
- The rules exist in one place (the skills); `copilot-instructions.md` only summarizes and links, so it must be updated when a key rule changes.
