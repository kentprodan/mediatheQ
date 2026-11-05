package io.github.kentprodan.mediatheq.feature.genre

import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/** A genre's page (#130). */
sealed interface GenreUiState {
    /** Before the first database read; shows only the top bar. */
    data object Loading : GenreUiState

    /** Nothing carries the genre (any more), or offline nothing downloaded does. */
    data object NotFound : GenreUiState

    data class Content(
        val genre: Genre,
        /** The albums tagged with the genre, by name. */
        val albums: List<Album>,
        /** The album artists of those albums, by name. */
        val artists: List<Artist>,
        /** The tracks tagged with the genre: the queue Play and Shuffle start. */
        val tracks: List<Track>,
        /** Every track shows, not only the first [GENRE_TOP_TRACKS] ("Show all"). */
        val allTracks: Boolean = false,
        /** The album grid columns the Library uses. */
        val albumColumns: Int = LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
        val currentTrackId: MediaId? = null,
    ) : GenreUiState {
        val shownTracks: List<Track> get() = if (allTracks) tracks else tracks.take(GENRE_TOP_TRACKS)
    }
}

/** Tracks the page shows before "Show all". */
const val GENRE_TOP_TRACKS = 10

sealed interface GenreAction {
    data object Play : GenreAction

    data object Shuffle : GenreAction

    /** A tap on a track: the default track action (#122) with the genre's tracks as the list. */
    data class PlayTrack(
        val track: Track,
    ) : GenreAction

    data object ShowAllTracks : GenreAction
}
