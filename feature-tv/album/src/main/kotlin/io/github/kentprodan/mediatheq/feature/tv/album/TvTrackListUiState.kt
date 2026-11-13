package io.github.kentprodan.mediatheq.feature.tv.album

import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

/** An album or playlist page on TV (#238): one layout for both. */
sealed interface TvTrackListUiState {
    data object Loading : TvTrackListUiState

    /** The album or playlist is not in the library (removed on the server). */
    data object NotFound : TvTrackListUiState

    data class Content(
        val header: TvTrackListHeader,
        /** The tracks, in play order; disc headers show when [discs] has more than one disc. */
        val discs: List<TvDisc>,
        /** The track that plays now, marked in the list. */
        val currentTrackId: MediaId? = null,
        /** Where Resume continues, `null` for no Resume button. */
        val resume: ResumePoint? = null,
    ) : TvTrackListUiState {
        val tracks: List<Track> get() = discs.flatMap { it.tracks }
    }
}

data class TvTrackListHeader(
    val title: String,
    /** The album artists, or nothing for a playlist. */
    val subtitle: String? = null,
    val year: Int? = null,
    val artwork: Artwork? = null,
    val trackCount: Int = 0,
    val duration: Duration = Duration.ZERO,
    /** The best quality of the tracks (an album), `null` for none or a playlist. */
    val quality: QualityLabel? = null,
    val isFavorite: Boolean = false,
)

data class TvDisc(
    val number: Int,
    val tracks: List<Track>,
)

sealed interface TvTrackListAction {
    data object Play : TvTrackListAction

    data object Shuffle : TvTrackListAction

    data object Resume : TvTrackListAction

    /** The page's own favorite (the album or playlist). */
    data object ToggleFavorite : TvTrackListAction

    /** Select on a track: the list from it (the Default track action). */
    data class PlayTrack(
        val index: Int,
    ) : TvTrackListAction

    data class PlayNext(
        val track: Track,
    ) : TvTrackListAction

    data class AddToQueue(
        val track: Track,
    ) : TvTrackListAction

    data class ToggleTrackFavorite(
        val track: Track,
    ) : TvTrackListAction
}
