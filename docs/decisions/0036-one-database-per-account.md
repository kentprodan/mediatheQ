# 0036. One database per account; switching accounts restarts the app

- Status: Accepted
- Date: 2026-11-11

## Context

#147 keeps several media server accounts, one active at a time (owner decision 2026-10-27: no merged library).
The Room database already carries `server_id` in every key, but the list queries (albums, artists, playlists, genres,
labels, search, offline lists) do not filter by server, the sync swaps whole tables and wipes the library when the
signed-in account changes, and the saved queue, waiting user data, resumes, download origins and waveforms live in the
same database. Every repository reads "the" account from `AccountStore.account`.

Options: one database with every query filtered by the active server and a per-server sync; one library re-synced on
every switch (needs the network, loses offline use and the queue's tracks); or one database file per account.

## Decision

- **Each account owns a Room database file**; its file name is the account's id. The account of the single-account app
  keeps `mediatheq.db` (DataStore migration `SingleToListMigration`); later ones get `mediatheq-<uuid>.db`.
- **The open database is chosen once per process** (`AccountDatabases`, SharedPreferences `accounts`, read before Room
  opens, like ADR 0020 and 0035). The active account is the one whose database is open, so `AccountStore.account` keeps
  its meaning and the repositories stay as they were.
- **Switching restarts the app** (`AppRestarter`, shared with the storage move of ADR 0035): playback pauses, the
  queue's place is saved, the other name is written with `commit()`, the process restarts on that account's library,
  queue (restored paused), waiting changes and resumes. No query changes, no schema change.
- **Adding** an account while one is active saves it beside it, inactive (its database is empty until it is first made
  active and synced). The same server and user again is the same account: its token is replaced (R9). With no active
  account a new login takes the open database; a saved one is opened by a restart.
- **Removing**: an inactive account is signed out, its token forgotten and its database deleted; the active one hands
  over to the next account (restart), or, being the last, signs out as before (onboarding). Its downloads go too (the
  dialog's checkbox, on by default: kept ones show again only when the account is added back), unless another account
  uses the same server. At start, databases no account owns are
  deleted, and only after the accounts were read successfully.
- **Shared things outside Room follow the active server**: the Media3 download index stays one; the Downloads tab, the
  offline lists and the origins see only the active server's downloads, and another account's unfinished downloads
  wait with stop reason 147 until it is active again. Covers of downloaded albums of other accounts' servers are kept.
- One DeviceId per install for every account: Jellyfin revokes tokens per DeviceId and user, so two users of one server
  can be saved together (to be confirmed by the manual test).
- A 401 signs the active account out as before (the account and its library go); other accounts stay saved.
- An account whose token cannot be decrypted (Keystore key lost) is left out of the list until signed in again; its
  database is kept. Only the active one failing counts as a storage error (signed out).

## Consequences

- A switch costs a restart (about a second; playback stops), said in the confirm dialog.
- Only the active account syncs; "Sync now" is offered for it alone.
- Two users of the same server share download files (same ids, same cache keys): removing a song in one account can
  delete the file another account's origin keeps.
- Editing an address changes every artwork URL: the image cache is cold once.
- Storage per account grows with each library (a few MB each).
