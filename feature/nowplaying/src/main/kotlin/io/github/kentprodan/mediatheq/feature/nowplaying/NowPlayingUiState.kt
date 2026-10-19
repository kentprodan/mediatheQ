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
}
