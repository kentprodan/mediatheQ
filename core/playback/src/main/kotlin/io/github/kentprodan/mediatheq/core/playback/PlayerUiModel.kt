package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlin.time.Duration

/** What the player is doing, for screens (architecture skill, section 7). The position comes separately, sampled. */
data class PlayerUiModel(
    /** The current queue entry, or `null` when nothing is queued. */
    val item: NowPlayingItem? = null,
    /** Shows a pause button (playing or about to play); `false` shows play. */
    val isPlaying: Boolean = false,
    val duration: Duration = Duration.ZERO,
    /** Transcodes cannot be seeked yet (jellyfin skill R12, #38). */
    val isSeekable: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** The queue entry playing, to mark it in [PlaybackConnection.queue]. */
    val currentEntryId: String? = null,
    /** Shuffle on: the queue shows the shuffled order and cannot be reordered (#41 adds the toggle). */
    val shuffle: Boolean = false,
)

/** One queue entry, for the queue screen. */
data class QueueItem(
    /** Unique in the queue (the same track can be queued twice); commands name entries by it. */
    val entryId: String,
    val id: MediaId,
    val title: String,
    val artist: String,
    val artwork: Artwork? = null,
    val duration: Duration = Duration.ZERO,
)

/** The playing track as the queue knows it. */
data class NowPlayingItem(
    val id: MediaId,
    val title: String,
    val artist: String,
    val artwork: Artwork? = null,
)
