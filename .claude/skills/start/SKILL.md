---
name: start
description: Start a work session on one GitHub issue - pick the issue, check dependencies, create the branch, read the relevant skills and ADRs, write a plan, get it critiqued, and get the owner's approval before writing code. Invoked by the owner as /start [issue number].
disable-model-invocation: true
---

# /start - begin one issue

One session = one issue. Never work on two issues in one branch.
Talk to the owner in Romanian; write code, commits, issues and PRs in English.
Every question to the owner uses AskUserQuestion with the recommended option first, marked "(Recommended)".

## 1. Pick the issue

- If an issue number was given (`/start 7`), use it.
- Otherwise: `gh issue list --state open --milestone "<current milestone>" --json number,title,body`, where the current milestone is the first open one in the order of ADR 0025 (never `Tests`: test issues are run with `/test`), and pick the lowest-numbered issue whose "Depends on" issues are all closed. Show it to the owner with AskUserQuestion (that issue recommended, plus up to 2 other ready issues).
- Read the full issue: `gh issue view <n> --comments`.
- If any "Depends on" issue is still open: stop and say which one must be done first.

## 2. Prepare the branch

```
git switch main
git pull --ff-only
git switch -c issue-<n>-<short-slug>
```
If the working tree is not clean, stop and ask what to do with the changes.

## 3. Load the rules

- Read every skill listed under **Skills** in the issue (`.claude/skills/<name>/SKILL.md`), fully.
- Always also read `architecture` and `testing`.
- Read `docs/decisions/README.md` and every ADR relevant to the issue.
- Look at the existing code the issue touches (modules, build files). Do not read the old Mac project unless the owner asks.

## 4. Write the plan

Present a short plan in Romanian:
- **Ce livrăm** - the acceptance criteria in plain words.
- **Module și fișiere** - new/changed modules and main files.
- **Pași** - ordered steps, each small enough for one commit.
- **Teste** - exactly which tests (from "Required tests" + the `testing` skill).
- **Decizii** - anything not already decided by an ADR or skill (versions, libraries, names). Each becomes an AskUserQuestion with a recommended option; decisions that change architecture or stack also get a new ADR in this branch.
- **Riscuri** - what could go wrong, and what is out of scope.

Check versions you plan to add against the official release pages; never guess a version.

## 5. Advisor review

Spawn one independent agent (fresh context) with the issue, the plan and the relevant skill files, and ask it to find: rule violations, missing tests, missing acceptance criteria, scope creep, simpler alternatives. Fold valid points into the plan and mention what changed in one line.

## 6. Approval

AskUserQuestion: "Aprobi planul?"
- "Aprob (Recommended)"
- "Vreau modificări" (then ask what, update, ask again)
- "Oprim" (stop; leave the branch)

Do not write production code before approval.

## 7. Work

- Small commits, each building and passing tests. Message format: `#<n>: <what>` in English.
- Tests together with the code they cover (bug fixes: failing test first).
- If something forces a change of plan or an unplanned decision, stop and ask (AskUserQuestion) instead of improvising.
- If a skill turns out wrong or incomplete, propose the skill change; edit it only after the owner agrees, in the same branch.
- When the acceptance criteria look done, tell the owner to run `/end`.
