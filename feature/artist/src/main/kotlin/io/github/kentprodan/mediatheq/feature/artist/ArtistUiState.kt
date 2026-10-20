package io.github.kentprodan.mediatheq.feature.artist

import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

sealed interface ArtistUiState {
    /** Before the first database read; shows only the top bar. */
    data object Loading : ArtistUiState

    /** The artist is not in the library (removed by a sync, or a stale link). */
    data object NotFound : ArtistUiState

    data class Content(
        val artist: Artist,
        /** Albums the artist is an album artist of, newest first. */
        val albums: List<Album>,
        /** Other albums with a track credited to the artist. */
        val appearsOn: List<Album>,
        /** Every track of the artist (own albums, then credits): the queue Play and Shuffle start. */
        val tracks: List<Track>,
        /** The most played tracks; empty hides the section. */
        val topTracks: List<Track>,
        /** The album grid columns the Library uses. */
        val albumColumns: Int = LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
        val currentTrackId: MediaId? = null,
    ) : ArtistUiState {
        /** Nothing to show or play: the page shows the empty state. */
        val isEmpty: Boolean get() = albums.isEmpty() && appearsOn.isEmpty() && tracks.isEmpty()
    }
}

sealed interface ArtistAction {
    data object Play : ArtistAction

    data object Shuffle : ArtistAction

    /** Play the top tracks starting at this one. */
    data class PlayTopTrack(
        val trackId: MediaId,
    ) : ArtistAction
}
