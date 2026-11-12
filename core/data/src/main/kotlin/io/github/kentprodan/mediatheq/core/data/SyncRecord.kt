package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import kotlin.time.Duration
import kotlin.time.Instant

/** How an account's last library sync ended (#148), kept across restarts: [error] is `null` when it succeeded. */
data class SyncRecord(
    val finishedAt: Instant,
    val error: MqError?,
    /** The tracks in the library after the sync; 0 when it failed. */
    val trackCount: Int,
    /** How long this run took (a resumed sync counts only the resumed part). */
    val duration: Duration,
)
