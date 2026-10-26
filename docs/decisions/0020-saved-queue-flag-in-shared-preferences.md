# 0020. A saved-queue flag in SharedPreferences for media buttons

- Status: Accepted
- Date: 2026-10-26

## Context

Play on headphones or a car kit with the app closed reaches Media3's `MediaButtonReceiver`, which starts the playback service in the foreground (#43). Such a service must then play and post its notification, or the system kills the app (`ForegroundServiceDidNotStartInTimeException`). With nothing to resume (signed out, no saved queue) it would crash. The receiver decides in `shouldStartForegroundService`, synchronously, on the main thread. Settings live in DataStore and the queue in Room; neither can be read at once there.

## Decision

- `SavedQueueFlag` in `:core:datastore`: one boolean in a private `SharedPreferences` file (`saved_queue`), read synchronously.
- `DefaultQueueRepository` sets it after saving a non-empty queue and clears it before clearing the queue. `QueueRepository.hasSavedQueue` exposes it.
- The queue is forgotten, and so the flag cleared, on logout and session expiry, on an empty save, and when the service's restore finds nothing to bring back (signed out, every track gone, unreadable).
- `MediatheqMediaButtonReceiver` starts the service only when the flag is set.

## Consequences

- SharedPreferences is used for this one flag only; settings stay in DataStore.
- The flag can briefly disagree with the database (written with `apply()`, a logout during a save). The worst case is one media button press that starts the service with nothing to play; the restore then clears the flag.
- A queue saved before this change has no flag: headphone Play resumes it only after the next save.
