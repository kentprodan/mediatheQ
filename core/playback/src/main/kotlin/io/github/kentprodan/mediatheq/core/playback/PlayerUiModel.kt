package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlin.time.Duration

/** What the player is doing, for screens (architecture skill, section 7). The position comes separately, sampled. */
data class PlayerUiModel(
    /** The current queue entry, or `null` when nothing is queued. */
    val item: NowPlayingItem? = null,
    /** Entries in the queue; 0 hides the mini player. */
    val queueSize: Int = 0,
    /** Shows a pause button (playing or about to play); `false` shows play. */
    val isPlaying: Boolean = false,
    val duration: Duration = Duration.ZERO,
    /** Transcodes cannot be seeked yet (jellyfin skill R12, #38). */
    val isSeekable: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** The playing track as the queue knows it. */
data class NowPlayingItem(
    val id: MediaId,
    val title: String,
    val artist: String,
    val albumTitle: String? = null,
    val artwork: Artwork? = null,
)
