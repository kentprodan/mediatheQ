package io.github.kentprodan.mediatheq.feature.genre

import io.github.kentprodan.mediatheq.core.data.GenreButton
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

/** A genre's page (#130, #134). */
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
        val albumArtists: List<Artist>,
        /** The tracks tagged with the genre, by name: the queue Play and Shuffle start. */
        val tracks: List<Track>,
        /** Every artist credited on those tracks, by name (#134). */
        val trackArtists: List<Artist> = emptyList(),
        val page: GenrePagePreferences = GenrePagePreferences(),
        /** The album grid columns the Library uses. */
        val albumColumns: Int = LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
        val currentTrackId: MediaId? = null,
    ) : GenreUiState {
        /** Every track, most played first, then by name: "Show all", and Top tracks before the cut. */
        val rankedTracks: List<Track> get() = rankTracks(tracks)

        /** As many of [rankedTracks] as the setting asks for. */
        val topTracks: List<Track> get() = rankedTracks.take(page.topTracksCount)

        /** The header's image: the most played album's cover (#134). */
        val headerArtwork: Artwork? get() = headerArtwork(tracks, albums, genre)

        val duration: Duration get() = tracks.fold(Duration.ZERO) { sum, track -> sum + track.duration }

        /** The sections that show, in the chosen order; switched off and empty ones are left out. */
        val sections: List<GenreSection>
            get() =
                page.sectionOrder.filter {
                    when (it) {
                        GenreSection.Artists -> page.showArtists && trackArtists.isNotEmpty()
                        GenreSection.AlbumArtists -> page.showAlbumArtists && albumArtists.isNotEmpty()
                        GenreSection.Albums -> page.showAlbums && albums.isNotEmpty()
                        GenreSection.TopTracks -> page.showTopTracks && tracks.isNotEmpty()
                    }
                }

        fun shows(button: GenreButton): Boolean = button !in page.hiddenButtons
    }
}

/** Most played first; the sort is stable, so unplayed and tied tracks keep their order by name. */
internal fun rankTracks(tracks: List<Track>): List<Track> = tracks.sortedByDescending { it.userData.playCount }

/**
 * The genre's picture (the server has none, jellyfin R17): the cover of the album whose tracks in the genre were
 * played most, else the first album with a cover, else the genre's own (its first album's).
 */
internal fun headerArtwork(
    tracks: List<Track>,
    albums: List<Album>,
    genre: Genre,
): Artwork? {
    val mostPlayed =
        tracks
            .filter { it.albumId != null && it.artwork != null }
            .groupBy { it.albumId }
            .values
            .maxByOrNull { group -> group.sumOf { it.userData.playCount } }
            ?.takeIf { group -> group.sumOf { it.userData.playCount } > 0 }
    return mostPlayed?.first()?.artwork ?: albums.firstNotNullOfOrNull { it.artwork } ?: genre.artwork
}

sealed interface GenreAction {
    data object Play : GenreAction

    data object Shuffle : GenreAction

    data object PlayNext : GenreAction

    data object AddToQueue : GenreAction

    /** A tap on a track: the default track action (#122) with the top tracks, or with every track ([all]). */
    data class PlayTrack(
        val track: Track,
        val all: Boolean = false,
    ) : GenreAction
}
