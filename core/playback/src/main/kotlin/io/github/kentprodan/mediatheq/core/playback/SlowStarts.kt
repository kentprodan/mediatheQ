package io.github.kentprodan.mediatheq.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long starting a track may take before the app says so (#122). */
val SLOW_START_THRESHOLD = 3.seconds

/**
 * The tracks whose start is slow (e.g. the server converting them, #122): buffering for [threshold] since they became
 * current, before they were ever ready, while the player wants to play (a restored queue prepared paused does not
 * count). Rebuffering in the middle of a track is not a slow start. Once per queue entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<PlayerUiModel>.slowStarts(threshold: Duration = SLOW_START_THRESHOLD): Flow<NowPlayingItem> =
    scan(StartWatch()) { watch, state -> watch.next(state) }
        .map { it.preparing }
        .distinctUntilChangedBy { it?.entryId }
        .transformLatest { preparing ->
            if (preparing != null) {
                delay(threshold)
                emit(preparing.item)
            }
        }

private data class Preparing(
    val entryId: String,
    val item: NowPlayingItem,
)

private data class StartWatch(
    val entryId: String? = null,
    val started: Boolean = false,
    val preparing: Preparing? = null,
) {
    fun next(state: PlayerUiModel): StartWatch {
        val entry = state.currentEntryId
        val started = (entry == entryId && started) || state.isReady
        val item = state.item
        return StartWatch(
            entry,
            started,
            if (entry != null && item != null && !started && state.isBuffering && state.isPlaying) {
                Preparing(entry, item)
            } else {
                null
            },
        )
    }
}
