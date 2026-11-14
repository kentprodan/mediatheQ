package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.Video
import kotlin.time.Duration

/** A movie's page (#273). */
sealed interface MovieUiState {
    data object Loading : MovieUiState

    data class Content(
        val movie: Video,
        /** "4K · HEVC · HDR10 · 5.1", `null` when unknown. */
        val format: String?,
        val externalSubtitles: Int,
        /** Where Resume starts; `null` shows only Play (never started, or watched). */
        val resumeAt: Duration?,
    ) : MovieUiState

    data class Error(
        val error: MqError,
    ) : MovieUiState
}

sealed interface MovieAction {
    data object ToggleFavorite : MovieAction

    data object ToggleWatched : MovieAction
}
