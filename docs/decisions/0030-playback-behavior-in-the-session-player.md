# 0030. Playback behavior lives in the session's player; weighted shuffle by default

- Status: Accepted
- Date: 2026-11-06

## Context

Issue #138 adds Settings -> Playback -> Playback: seven switches that change how the queue and the transport buttons behave (Play next order, rewind first, circular navigation, keep paused, stop repeat one, retain player state, weighted shuffle). They must act the same from the app, the notification, headset buttons and Android Auto. Until now shuffle orders were uniform (`shuffleFrom`), Media3 decided next / previous, and repeat carried over to a new queue.

## Decision

- **One place:** `QueuePlayer`, the `ForwardingPlayer` the `MediaLibrarySession` exposes, applies every rule. Every controller (our app, System UI, headsets through the session, Android Auto) reaches the player through it. The rules themselves are pure functions (`QueueRules.kt`).
- **Circular navigation:** Media3 drops next / previous from the available commands at the ends of the queue, and the session forwards exactly the commands its player listener receives. `QueuePlayer` adds them back in `getAvailableCommands()` and in the commands its listeners receive (a `java.lang.reflect.Proxy` around each listener, so every other callback passes unchanged).
- **Weighted shuffle, on by default:** a weighted random permutation (Efraimidis-Spirakis). An entry played today weighs 0.2, rising to 1 after 30 days (never played: 1). A favorite weighs x1.5, a rating x0.75 to x1.25. The inputs travel in the media item extras. Off keeps the uniform `shuffleFrom`.
- **Retain player state, off by default:** a new queue starts with repeat off, and with shuffle off unless Shuffle asked for it. On, both carry over. The system resuming the saved queue is not a new queue (`QueuePersistence.resuming`).
- Automatic skips (a track that cannot load) use the ExoPlayer itself, not these rules.

## Consequences

- Repeat no longer carries over to a new queue by default (it did before #138).
- Notification and Android Auto show next / previous at the ends of the queue while circular navigation is on.
- The Play next mark is not saved with the queue (a `shortcut:` in `TrackMediaItems.kt`). After a restart, earlier Play next entries count as plain ones.
- Weighted shuffle uses what the server knew when the track was queued (last played, favorite, rating).
