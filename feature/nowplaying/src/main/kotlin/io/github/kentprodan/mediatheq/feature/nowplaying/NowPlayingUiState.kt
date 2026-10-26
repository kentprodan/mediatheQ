package io.github.kentprodan.mediatheq.feature.nowplaying

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.RepeatMode
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
        val repeatMode: RepeatMode = RepeatMode.Off,
        val isFavorite: Boolean = false,
        /** 1-10 half stars, `null` when unrated. */
        val halfStars: Int? = null,
        val ratingMode: RatingMode = RatingMode.WholeStars,
        /** The sample rate sent unchanged to a USB DAC; `null` when not bit-perfect. */
        val bitPerfectSampleRateHz: Int? = null,
        /** The track is Dolby Atmos but plays as a stereo conversion: this device and output cannot play Atmos. */
        val atmosConverted: Boolean = false,
    ) : NowPlayingUiState
}

/** What the Lyrics screen shows for the playing track. */
sealed interface LyricsUiState {
    data object Loading : LyricsUiState

    /** Synced or plain: see [Lyrics.isSynced]. */
    data class Content(
        val lyrics: Lyrics,
    ) : LyricsUiState

    /** The track has no lyrics. */
    data object None : LyricsUiState

    data class Error(
        val error: MqError,
    ) : LyricsUiState
}

sealed interface NowPlayingAction {
    data object TogglePlayPause : NowPlayingAction

    data object Next : NowPlayingAction

    data object Previous : NowPlayingAction

    /** Seek to [fraction] (0..1) of the current track. */
    data class SeekTo(
        val fraction: Float,
    ) : NowPlayingAction

    /** Seek to [position] of the current track (a tapped lyrics line). */
    data class SeekToTime(
        val position: Duration,
    ) : NowPlayingAction

    data class SetShuffle(
        val enabled: Boolean,
    ) : NowPlayingAction

    /** Repeat off -> all -> one -> off. */
    data object CycleRepeat : NowPlayingAction

    /** Ask for the playing track's lyrics again after an error. */
    data object RetryLyrics : NowPlayingAction

    data class SkipToQueueItem(
        val entryId: String,
    ) : NowPlayingAction

    data class RemoveQueueItem(
        val entryId: String,
    ) : NowPlayingAction

    data class SetFavorite(
        val isFavorite: Boolean,
    ) : NowPlayingAction

    /** Rate the playing track with [halfStars], 1-10; `null` clears the rating. */
    data class Rate(
        val halfStars: Int?,
    ) : NowPlayingAction

    /** Move the entry to position [to] of the queue. */
    data class MoveQueueItem(
        val entryId: String,
        val to: Int,
    ) : NowPlayingAction
}
