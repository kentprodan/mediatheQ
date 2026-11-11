# 0034. Bluetooth lyrics ride in the session title, the real title in the display title

- Status: Accepted
- Date: 2026-11-11

## Context

Issue #143: with "Bluetooth lyrics" on, car and Bluetooth displays show the current synced lyrics line where the track title is, while mediatheQ's own screens, the notification and the system media card keep the real title. A Bluetooth display reads the platform media session, and so does the system media card. The issue left open whether to change the session metadata or to keep a separate session.

What the platform does (AOSP main and Media3 1.11.1 sources, read 2026-11-11):

- Media3 publishes the session player's `getMediaMetadata().title` as `METADATA_KEY_TITLE` and `.displayTitle` as `METADATA_KEY_DISPLAY_TITLE`.
- Bluetooth (AVRCP, `audio_util/helpers/Metadata.java`) reads only `METADATA_KEY_TITLE`.
- The system media card (SystemUI `MediaDataLoader`) prefers `METADATA_KEY_DISPLAY_TITLE`, then `METADATA_KEY_TITLE`.
- Bluetooth (`MediaPlayerWrapper.isMetadataSynced`) compares the session's title and artist with the active item of the session queue and holds an update back for 2 seconds while they differ. Without a queue it sends at once.
- mediatheQ's screens read the title from the queue's `MediaItem`, not from the player metadata.

## Decision

- One session. `QueuePlayer`, the session's player (ADR 0030), returns the metadata with `title` = the lyrics line and `displayTitle` = the real title while a line is set, in `getMediaMetadata()` and in the value its listeners receive. The queue items and Room never change.
- The media notification's content title is `displayTitle ?: title` (`DefaultMediaNotificationProvider` subclass).
- While the setting is on, the platform session publishes no queue: the media notification controller, whose commands Media3 applies to the platform session, loses `COMMAND_GET_TIMELINE`. Media3 controllers (the app) keep the queue.
- `BluetoothLyrics` in the service loads the playing track's lyrics once (only with the setting on), publishes the line at the player's position whenever it fires, and schedules the next change at the next line's start, never sooner than 500 ms after the last one and never for an unchanged text.

## Consequences

- With the setting on, a car's track list over Bluetooth is empty, and scrobblers or other apps that read the session title see lyrics lines. The setting's summary says so. Default off.
- A head unit may treat each title change as a new track. Only the car tells (manual test).
- The lyrics load is one more server request per track from the background; like any request, an expired session ends in sign-out.
- Android Auto and Wear read the same platform session; how lyrics look there is decided with those milestones.
