# 0033. Automatic actions work only while the playback service lives

- Status: Accepted
- Date: 2026-11-10

## Context

Issue #141 adds Settings -> Playback -> Automatic actions: playback starts when headphones or a Bluetooth device connect, pauses at volume 0 and plays on when the volume is raised, pauses when the app is swiped away, and treats a stop from outside as a pause. The issue asked for auto play also with the app swiped away, with a banner asking the user to turn battery optimization off.

What Android allows (developer.android.com, read 2026-11-10; the app targets API 37):

- Starting a foreground service from the background (API 31+) needs an exemption. Battery optimization off is one; a Bluetooth broadcast no longer is on the documented list.
- Android 17 "background audio hardening": an app in the background may play, ask for audio focus or change the volume only from a foreground service with while-in-use capability. A service has it when it started while the app was visible or from an explicit user action (a notification, a widget, a media key from an external device). A service started from the background by a broadcast (Bluetooth, USB, boot) does not, whatever the battery setting: playback is silenced and the focus request fails.
- Media3 keeps a paused service in the foreground for 10 minutes, then lets go of it.

So a start from a device broadcast cannot play on Android 17, and the battery banner would not change that.

## Decision

- Automatic actions run inside `MediatheqPlaybackService` only. No manifest receiver for Bluetooth or USB, no `BLUETOOTH_CONNECT` permission, no battery optimization banner, no keep-alive of any kind.
- **Auto play:** one `AudioDeviceCallback` in the service. An added output of a wired / USB type or a Bluetooth type plays what is queued (the service restores the saved queue when it starts). It covers LE Audio too and needs no permission. The first callback (devices already there) is ignored.
- **Volume:** a receiver for the platform's volume broadcast, registered while the service lives (Media3's own `StreamVolumeManager` listens the same way; ExoPlayer's device volume listener needs device volume control on, which would also hand controllers the volume commands).
- **Stop as pause:** in `QueuePlayer`, the session's player (ADR 0030), so every controller gets it. The media notification is exempt: dismissing the media card sends a stop, and a pause would bring the card back.
- The decision (event + settings + state -> play / pause / nothing) is one pure function, `autoAction`.
- Each setting's summary states the limit: it works while the app is open or up to about 10 minutes after a pause, and not once the app is swiped away while paused.

## Consequences

- With the app closed, a headset or car that sends Play itself on connect still starts playback through the media button path (#43, ADR 0020); a device that only connects does not.
- After the 10 minutes, a play from the service is refused by the platform (no focus) and nothing happens.
- If a later Android version or a companion-device pairing allows a background start with audio, auto play with the app closed is a new issue, not a change here.
