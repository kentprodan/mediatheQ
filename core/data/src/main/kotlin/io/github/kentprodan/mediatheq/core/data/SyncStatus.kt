package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import kotlin.time.Instant

/** Progress of the library sync, for screens and pull-to-refresh. */
sealed interface SyncStatus {
    /** The last complete sync, `null` before the first one (and while a sync runs). */
    val lastSyncedAt: Instant?

    /** Not syncing; [lastSyncedAt] is the last complete sync, `null` before the first. */
    data class Idle(
        override val lastSyncedAt: Instant?,
    ) : SyncStatus

    /** Fetching [step]; [fetched] of [total] items of that step so far. */
    data class Running(
        val step: SyncStep,
        val fetched: Int,
        val total: Int,
    ) : SyncStatus {
        override val lastSyncedAt: Instant? get() = null
    }

    /** The last sync stopped with [error]; the library on the phone is the previous complete one. */
    data class Failed(
        val error: MqError,
        override val lastSyncedAt: Instant?,
    ) : SyncStatus
}

/** The parts of a full sync, in order. */
enum class SyncStep { AlbumArtists, Albums, Tracks, Playlists, PlaylistEntries }
