package io.github.kentprodan.mediatheq.feature.artist

import io.github.kentprodan.mediatheq.core.data.ArtistButton
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlin.time.Duration

sealed interface ArtistUiState {
    /** Before the first database read; shows only the top bar. */
    data object Loading : ArtistUiState

    /** The artist is not in the library (removed by a sync, or a stale link). */
    data object NotFound : ArtistUiState

    data class Content(
        val artist: Artist,
        /** Albums the artist is an album artist of, in the page's album order. */
        val albums: List<Album>,
        /** Other albums with a track credited to the artist. */
        val appearsOn: List<Album>,
        /** Every track of the artist: own albums' tracks in [albums]' order, then credits. */
        val tracks: List<Track>,
        /** Every played track, most played first: "Show all" of the top tracks. */
        val playedTracks: List<Track>,
        /** The genres of its own albums (#133). */
        val genres: List<String> = emptyList(),
        val page: ArtistPagePreferences = ArtistPagePreferences(),
        val currentTrackId: MediaId? = null,
        val ratingMode: RatingMode = RatingMode.WholeStars,
        /** Multi-select of the top tracks and album cards (#123). */
        val selection: ListSelection<MediaId> = ListSelection(),
        /** Tracks of a selection to add to a playlist: the picker opens, then [ArtistAction.AddToPlaylistShown]. */
        val addToPlaylist: List<MediaId>? = null,
    ) : ArtistUiState {
        /** The most played tracks, as many as the setting asks for. */
        val topTracks: List<Track> get() = playedTracks.take(page.topTracksCount)

        /** All tracks' length, for the header's "albums · tracks · duration". */
        val duration: Duration get() = tracks.fold(Duration.ZERO) { sum, track -> sum + track.duration }

        /** The sections that show, in the chosen order; empty ones are left out. */
        val sections: List<ArtistSection>
            get() =
                page.sectionOrder.filter {
                    when (it) {
                        ArtistSection.Albums -> albums.isNotEmpty()
                        ArtistSection.AppearsOn -> page.showAppearsOn && appearsOn.isNotEmpty()
                        ArtistSection.TopTracks -> page.showTopTracks && playedTracks.isNotEmpty()
                        ArtistSection.About -> page.showAbout && hasAbout
                    }
                }

        /** A biography, genres, counts, favorite or rating: something for About to show. */
        private val hasAbout: Boolean
            get() =
                artist.overview != null || genres.isNotEmpty() || albums.isNotEmpty() || tracks.isNotEmpty() ||
                    artist.userData.isFavorite || ratingMode != RatingMode.Off

        fun shows(button: ArtistButton): Boolean = button !in page.hiddenButtons

        /** Every selectable item, in the order shown: top tracks and album cards of the shown sections. */
        val selectableIds: List<MediaId>
            get() =
                sections.flatMap { section ->
                    when (section) {
                        ArtistSection.TopTracks -> topTracks.map { it.id }
                        ArtistSection.Albums -> albums.map { it.id }
                        ArtistSection.AppearsOn -> appearsOn.map { it.id }
                        ArtistSection.About -> emptyList()
                    }
                }

        /** Nothing to show or play: the page shows the empty state. */
        val isEmpty: Boolean get() = albums.isEmpty() && appearsOn.isEmpty() && tracks.isEmpty()
    }
}

sealed interface ArtistAction {
    /** Plays what "Play button action" says (#133). */
    data object Play : ArtistAction

    data object Shuffle : ArtistAction

    /** The artist's tracks in album order, after the playing one. */
    data object PlayNext : ArtistAction

    data object AddToQueue : ArtistAction

    /** Downloads the artist's own albums. */
    data object Download : ArtistAction

    /** Play the top tracks starting at this one; [all] plays every played track ("Show all"). */
    data class PlayTopTrack(
        val trackId: MediaId,
        val all: Boolean = false,
    ) : ArtistAction

    data class SetFavorite(
        val isFavorite: Boolean,
    ) : ArtistAction

    /** Rate the artist with [halfStars], 1-10; `null` clears the rating. */
    data class Rate(
        val halfStars: Int?,
    ) : ArtistAction

    /** A swipe on a track to the right (or left): the action chosen in Settings -> Media lists (#123). */
    data class SwipeTrack(
        val track: Track,
        val right: Boolean,
    ) : ArtistAction

    /** Rates a track (a Rate swipe): [halfStars] 1-10, `null` clears. */
    data class RateTrack(
        val track: Track,
        val halfStars: Int?,
    ) : ArtistAction

    /** A long press starts selecting tracks (#123); while selecting a tap toggles one. */
    data class ToggleSelection(
        val id: MediaId,
    ) : ArtistAction

    data object SelectAll : ArtistAction

    /** Clear or Back: selecting ends. */
    data object ClearSelection : ArtistAction

    data object AddToPlaylistShown : ArtistAction

    /** A selection toolbar action on the selected tracks and albums' tracks; selecting ends. */
    data class OnSelection(
        val command: SelectionCommand,
    ) : ArtistAction
}
