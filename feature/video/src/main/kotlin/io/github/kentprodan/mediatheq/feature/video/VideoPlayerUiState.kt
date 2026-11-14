package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.common.MqError
import kotlin.time.Duration

/** What the video player shows (ADR 0049). The position comes apart, sampled by the connection. */
sealed interface VideoPlayerUiState {
    /** The video is read or its stream is being asked for. */
    data object Loading : VideoPlayerUiState

    data class Playing(
        val title: String,
        /** "Series · S2 E4" or the year. */
        val subtitle: String?,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val duration: Duration,
        val isSeekable: Boolean,
        /** Width / height once the first frame is known: wide videos turn the screen to landscape. */
        val aspectRatio: Float?,
        val controlsShown: Boolean,
    ) : VideoPlayerUiState

    /** The video is no longer the player's (it ended and the music came back, or something else was played). */
    data object Ended : VideoPlayerUiState

    /** Not in the library ([MqError.NotFound]) or it could not play. */
    data class Error(
        val error: MqError,
    ) : VideoPlayerUiState
}

sealed interface VideoPlayerAction {
    data object TogglePlayPause : VideoPlayerAction

    data class SeekTo(
        val position: Duration,
    ) : VideoPlayerAction

    /** A tap on the picture: shows the controls, or hides them when shown. */
    data object ToggleControls : VideoPlayerAction

    /** Back (ADR 0047): the video pauses and the page under the player shows again. */
    data object Leave : VideoPlayerAction
}
