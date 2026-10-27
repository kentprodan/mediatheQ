# 0025. Milestones by platform, a Polish milestone and a Tests milestone

- Status: Accepted
- Date: 2026-10-27
- Supersedes: 0002

## Context

ADR 0002 ordered the work in phases F0-F6. Their names did not say which app a phase is about, so the owner could not
see at a glance what exists on the phone and where interface changes belong. Manual tests lived in
`docs/manual-tests/queue.md`, invisible on GitHub: a closed feature issue did not say whether it was verified on the
phone.

## Decision

Milestones are named by platform (or by server provider) and run in this order:

| Order | Milestone | Was | Content |
|---|---|---|---|
| 1 | Mobile 0 · Foundation | F0 | project setup, CI, design system, navigation shell, server login (done) |
| 2 | Mobile 1 · Music | F1 | the music app on the Pixel 9; exit: a week of daily use instead of Symfonium (#72) |
| 3 | Mobile 2 · Polish | new | the owner's interface changes and polish of the phone app |
| 4 | TV 1 · Music + Atmos | F2 | Compose for TV music app with E-AC-3 JOC / TrueHD passthrough |
| 5 | Mobile 3 · Video, TV 2 · Video | F3 | video on the phone and on the TV (split by platform) |
| 6 | Servers 1 · Navidrome | F4 | second provider |
| 7 | Auto & Wear 1 | F5 | Android Auto, Cast, Wear OS |
| 8 | Servers 2 · Plex + extras | F6 | Plex provider and extras |

ADR 0002's other rules stay: no video code before Video, TV 1 is music only.

Manual tests get their own **Tests** milestone: one issue per delivered feature ("Test: #<n> <title>", label
`type:test`), its steps as a checklist. A feature issue closes with its PR; its test issue stays open until `/test`
passes every step; a failed step becomes a bug in the feature's milestone. `docs/manual-tests/queue.md` is retired
(its pending items became #92-#110; the queue's history stays in git). The `start`, `end` and `test` skills follow
this; `/start` never picks from Tests.

## Consequences

- Closed issues of a platform milestone = what that app has; open test issues = what still needs checking on hardware.
- Interface changes for the phone go to Mobile 2 · Polish, before the TV work starts.
- Older text may still say F1, F2, ...; read them with the table above.
