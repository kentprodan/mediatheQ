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
        /** Its audio and subtitle streams and what plays (#274); `null` while read. */
        val tracks: VideoTracks? = null,
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

    /** The sheet's audio row (#274): saved for this video, then it plays on with it. */
    data class ChooseAudio(
        val index: Int,
    ) : VideoPlayerAction

    /** The sheet's subtitle row; `null` = Off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : VideoPlayerAction

    /** The countdown's Cancel: the next episode is taken off, the video stops at its end (#275). */
    data object CancelNext : VideoPlayerAction

    /** The countdown's Play now. */
    data object PlayNextNow : VideoPlayerAction

    /** An episode's choice for its whole season, or for the episode alone. */
    data class WholeSeason(
        val on: Boolean,
    ) : VideoPlayerAction
}
