---
name: end
description: Finish the current issue - run all checks, verify acceptance criteria and skill done checklists, get an independent code review, open the pull request, queue manual tests, and merge after approval. Invoked by the owner as /end.
disable-model-invocation: true
---

# /end - finish the current issue

Talk to the owner in Romanian; PR text in English. Questions via AskUserQuestion, recommended option first.

## 1. Identify the issue

From the branch name `issue-<n>-...`. Read the issue again (`gh issue view <n>`). If not on an issue branch, stop.

## 2. Run the checks

Run every task that exists in the project (skip ones not set up yet and say so):
```
./gradlew spotlessApply
./gradlew assembleDebug testDebugUnitTest lint
./gradlew validateDebugScreenshotTest
```
(On Windows PowerShell use `.\gradlew.bat`.)
- Any failure: fix it and rerun. Never disable, skip or delete a test to get green; never raise the screenshot threshold.
- If screenshot references changed intentionally, regenerate with `updateDebugScreenshotTest`, open the HTML report, and list every changed image with the reason.

## 3. Verify completeness

- Go through every **Acceptance criteria** and **Required tests** item of the issue. For each: done (where in the code/tests) or not done.
- Go through the **Done checklist** of every skill listed in the issue plus `architecture` and `testing`.
- Anything not done: finish it, or ask the owner (AskUserQuestion) whether to split it into a new issue. Never silently drop a criterion.

## 4. Independent review

Spawn one reviewer agent with fresh context. Give it: the issue, `git diff main...HEAD`, and the relevant skill files. Ask for real defects only: bugs, rule violations (architecture, design tokens, auth, logging of secrets), missing or weak tests, dead code. Fix valid findings, rerun step 2, and summarize what changed in one line per finding.

## 5. Manual tests

For each "Manual test" item of the issue, append an entry to `docs/manual-tests/queue.md` (create it if missing):

```
## #<n> <issue title>
- [ ] <device>: <steps> -> <expected result>
```

## 6. Pull request

```
git push -u origin HEAD
gh pr create --title "#<n>: <issue title>" --body-file <tmp file>
```
PR body (English):
- `Closes #<n>`
- **What**: 3-6 bullets
- **Tests**: which were added
- **Screenshots changed**: list or "none"
- **Decisions**: ADRs added/changed or "none"
- **Manual tests queued**: list or "none"

Then tick the completed checkboxes in the issue body (`gh issue edit <n> --body-file ...`).

## 7. Merge

Wait for CI on the PR (`gh pr checks --watch`). If red: fix, push, wait again.
Then AskUserQuestion: "CI e verde. Fac merge?"
- "Da, squash merge (Recommended)" -> `gh pr merge --squash --delete-branch`, then `git switch main && git pull --ff-only`
- "Nu încă" -> leave the PR open

Finish with 2-3 lines in Romanian: what was delivered, manual tests waiting for `/test`, and the next ready issue.
