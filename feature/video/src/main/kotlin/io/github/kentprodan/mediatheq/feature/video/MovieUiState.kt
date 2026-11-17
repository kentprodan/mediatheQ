package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.VideoDownload
import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.data.externalSubtitles
import io.github.kentprodan.mediatheq.core.model.RemoteSession
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
        /** Its audio and subtitle streams and what will play (#274); `null` while read. */
        val tracks: VideoTracks? = null,
        /** Its download (#279), `null` when not downloaded. */
        val download: VideoDownload? = null,
        /** TVs running mediatheQ that can play it (#389); empty hides Play on TV. */
        val tvs: List<RemoteSession> = emptyList(),
        val sentToTv: SentToTv? = null,
        /** Cast and crew from the server (#442), leads first; empty offline or while read. */
        val people: List<io.github.kentprodan.mediatheq.core.model.Person> = emptyList(),
    ) : MovieUiState

    data class Error(
        val error: MqError,
    ) : MovieUiState
}

sealed interface MovieAction {
    data object ToggleFavorite : MovieAction

    data object ToggleWatched : MovieAction

    /** The tracks sheet's audio row (#274): saved for this movie. */
    data class ChooseAudio(
        val index: Int,
    ) : MovieAction

    /** The sheet's subtitle row; `null` = Off. */
    data class ChooseSubtitle(
        val index: Int?,
    ) : MovieAction

    /** Plays it on [tv] from where the phone is (#389). */
    data class PlayOnTv(
        val tv: RemoteSession,
    ) : MovieAction

    /** Download it as its original (#279), or remove the download. */
    data object ToggleDownload : MovieAction
}

/** The last Play on TV (#389): on which device, and whether the server took it. */
data class SentToTv(
    val deviceName: String,
    val ok: Boolean,
)
