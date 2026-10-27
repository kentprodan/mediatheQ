---
name: test
description: Run the open manual test issues (Tests milestone) on real devices (Pixel 9, Google TV Streamer, USB DAC, soundbar) over adb via Wi-Fi or Tailscale, guide the owner step by step, record results on the issues, and file bugs for failures. Invoked by the owner as /test.
disable-model-invocation: true
---

# /test - manual tests on real hardware

Talk to the owner in Romanian. Questions via AskUserQuestion, recommended option first. Keep each step short: the owner has the phone in hand.

## 1. Choose what to test

- `gh issue list --milestone Tests --state open --json number,title,body`: each issue is one feature's manual test, its steps are the unticked checkboxes. If none, say so and stop.
- AskUserQuestion (multiSelect): which test issues to run now; issues for the same device first. Say which steps you can run yourself over adb (UI, screenshots, airplane mode, playback state) and which need the owner (listening, feel, TalkBack, mobile data, extra hardware).

## 2. Connect the device

- `adb devices`. If the needed device is missing, ask for its address (Wi-Fi IP or Tailscale name) and run `adb connect <address>:<port>`. For Android 11+ wireless debugging, the owner may need to pair first (`adb pair <address>:<pair-port>`); explain where to find the code on the phone in one line.
- Install the build under test from `main` (or the PR branch if the owner says so): `./gradlew installDebug` (Windows PowerShell: `.\gradlew.bat installDebug`).

## 3. Run each item

For each item:
1. Tell the owner exactly what to do (1-4 short steps) and what should happen.
2. Where it helps, collect evidence yourself: `adb exec-out screencap -p > <file>.png`, `adb logcat -d -s <tags>` (never log tokens), `adb shell dumpsys media_session` / `dumpsys audio` for playback state and output format.
3. AskUserQuestion: "Rezultat?" -> "A mers (Pass)" / "Nu a mers (Fail)" / "Sar peste (Skip)". On Fail, ask for a one-line description of what happened.

## 4. Record results

- In each test issue tested: tick passed steps and append ` - passed YYYY-MM-DD` (`gh issue edit <n> --body-file ...`); skipped steps stay unticked; failed steps stay unticked with ` - failed YYYY-MM-DD, see #<bug>`.
- Add a comment to each test issue: date, device, app version/commit, each step with Pass/Fail/Skip and notes.
- For each Fail: create a bug issue with the bug template fields (what happened, expected, steps, device, logs), labels `type:bug` + matching `area:*`, the milestone of the feature the test belongs to, and link it.
- Close a test issue when every step passed. Steps that turn out obsolete (the feature changed since) are ticked with ` - superseded by #<n>` and a one-line reason.

## 5. Wrap up

2-3 lines in Romanian: what passed, what failed (bug numbers), what is still pending.
