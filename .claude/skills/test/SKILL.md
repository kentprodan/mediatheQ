---
name: test
description: Run queued manual tests on real devices (Pixel 9, Google TV Streamer, USB DAC, soundbar) over adb via Wi-Fi or Tailscale, guide the owner step by step, record results on the issues, and file bugs for failures. Invoked by the owner as /test.
disable-model-invocation: true
---

# /test - manual tests on real hardware

Talk to the owner in Romanian. Questions via AskUserQuestion, recommended option first. Keep each step short: the owner has the phone in hand.

## 1. Choose what to test

- Read `docs/manual-tests/queue.md`. If empty, say so and stop.
- AskUserQuestion (multiSelect): which pending items to run now, grouped by issue; items for the same device first.

## 2. Connect the device

- `adb devices`. If the needed device is missing, ask for its address (Wi-Fi IP or Tailscale name) and run `adb connect <address>:<port>`. For Android 11+ wireless debugging, the owner may need to pair first (`adb pair <address>:<pair-port>`); explain where to find the code on the phone in one line.
- Install the build under test from `main` (or the PR branch if the owner says so): `./gradlew installDebug` (Windows PowerShell: `.\gradlew.bat installDebug`).

## 3. Run each item

For each item:
1. Tell the owner exactly what to do (1-4 short steps) and what should happen.
2. Where it helps, collect evidence yourself: `adb exec-out screencap -p > <file>.png`, `adb logcat -d -s <tags>` (never log tokens), `adb shell dumpsys media_session` / `dumpsys audio` for playback state and output format.
3. AskUserQuestion: "Rezultat?" -> "A mers (Pass)" / "Nu a mers (Fail)" / "Sar peste (Skip)". On Fail, ask for a one-line description of what happened.

## 4. Record results

- For each issue tested, add a comment (`gh issue comment <n>`): date, device, app version/commit, each item with Pass/Fail/Skip and notes.
- In `docs/manual-tests/queue.md`: tick passed items and append ` - passed YYYY-MM-DD`; keep skipped items unticked; failed items stay unticked with ` - failed YYYY-MM-DD, see #<bug>`.
- For each Fail: create a bug issue with the bug template fields (what happened, expected, steps, device, logs), labels `type:bug` + matching `area:*`, milestone of the original issue, and link it.
- Commit the queue file update on a short branch `manual-tests-YYYY-MM-DD`, open a PR, and merge it after CI passes if the owner agrees.

## 5. Wrap up

2-3 lines in Romanian: what passed, what failed (bug numbers), what is still pending.
