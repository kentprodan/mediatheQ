package io.github.kentprodan.mediatheq.feature.nowplaying

import io.github.kentprodan.mediatheq.core.model.Artwork
import kotlin.time.Duration

/** What the mini player and Now Playing show. */
sealed interface NowPlayingUiState {
    /** Nothing queued: no mini player. */
    data object Hidden : NowPlayingUiState

    data class Playing(
        val title: String,
        val artist: String,
        val artwork: Artwork?,
        val isPlaying: Boolean,
        val duration: Duration,
        val isSeekable: Boolean,
        val hasNext: Boolean,
        val hasPrevious: Boolean,
        /** The queue entry playing, marked on the queue screen. */
        val currentEntryId: String? = null,
        /** Shuffled queues cannot be reordered. */
        val shuffle: Boolean = false,
        val isFavorite: Boolean = false,
        /** 1-5, `null` when unrated. */
        val stars: Int? = null,
    ) : NowPlayingUiState
}

sealed interface NowPlayingAction {
    data object TogglePlayPause : NowPlayingAction

    data object Next : NowPlayingAction

    data object Previous : NowPlayingAction

    /** Seek to [fraction] (0..1) of the current track. */
    data class SeekTo(
        val fraction: Float,
    ) : NowPlayingAction

    data class SkipToQueueItem(
        val entryId: String,
    ) : NowPlayingAction

    data class RemoveQueueItem(
        val entryId: String,
    ) : NowPlayingAction

    data class SetFavorite(
        val isFavorite: Boolean,
    ) : NowPlayingAction

    /** Rate the playing track with 1-5 [stars]; `null` clears the rating. */
    data class Rate(
        val stars: Int?,
    ) : NowPlayingAction

    /** Move the entry to position [to] of the queue. */
    data class MoveQueueItem(
        val entryId: String,
        val to: Int,
    ) : NowPlayingAction
}
