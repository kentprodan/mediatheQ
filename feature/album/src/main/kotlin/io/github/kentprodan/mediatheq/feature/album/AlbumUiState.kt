package io.github.kentprodan.mediatheq.feature.album

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

sealed interface AlbumUiState {
    /** Before the first database read; shows only the top bar (it is too short for a loading indicator). */
    data object Loading : AlbumUiState

    /** The album is not in the library (removed by a sync, or a stale link). */
    data object NotFound : AlbumUiState

    data class Content(
        val album: Album,
        val discs: List<Disc>,
        /** The track the player is on, marked in the list (any album's; only this album's shows). */
        val currentTrackId: MediaId? = null,
    ) : AlbumUiState {
        /** All tracks in play order, the queue Play and Shuffle start. */
        val tracks: List<Track> get() = discs.flatMap { it.tracks }

        /** Counted from the tracks shown, so the header always matches the list. */
        val trackCount: Int get() = discs.sumOf { it.tracks.size }

        val duration: Duration get() = tracks.fold(Duration.ZERO) { total, track -> total + track.duration }

        /** Disc headers only when the album has more than one disc. */
        val showDiscHeaders: Boolean get() = discs.size > 1
    }
}

/** One disc of an album; tracks without a disc number belong to disc 1. */
data class Disc(
    val number: Int,
    val tracks: List<Track>,
)

sealed interface AlbumAction {
    data object Play : AlbumAction

    data object Shuffle : AlbumAction

    /** Play the album starting at this track (an id, not a list position: disc headers shift positions). */
    data class PlayTrack(
        val trackId: MediaId,
    ) : AlbumAction

    /** Queue one track right after the playing one, or the whole album when [trackId] is `null`. */
    data class PlayNext(
        val trackId: MediaId? = null,
    ) : AlbumAction

    /** Queue one track at the end, or the whole album when [trackId] is `null`. */
    data class AddToQueue(
        val trackId: MediaId? = null,
    ) : AlbumAction

    /** Favorite or not: the album itself or one of its tracks. */
    data class SetFavorite(
        val id: MediaId,
        val isFavorite: Boolean,
    ) : AlbumAction

    /** Rate the album with 1-5 [stars]; `null` clears the rating. */
    data class Rate(
        val stars: Int?,
    ) : AlbumAction
}
