package io.github.kentprodan.mediatheq.feature.album

import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.AlbumResume
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AlbumTitle
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.QualityLabel
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import io.github.kentprodan.mediatheq.core.ui.albumQuality
import io.github.kentprodan.mediatheq.core.ui.albumTitle
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
        val ratingMode: RatingMode = RatingMode.WholeStars,
        /** Multi-select of the shown tracks (#123). */
        val selection: ListSelection<MediaId> = ListSelection(),
        /** Settings -> Album page (#132). */
        val page: AlbumPagePreferences = AlbumPagePreferences(),
        /** Where Resume starts (#132); `null` hides it (never played, or finished). */
        val resume: AlbumResume? = null,
        /** The first album artist's other albums, sorted as chosen (#132). */
        val moreAlbums: List<Album> = emptyList(),
        val genres: List<String> = emptyList(),
        val labels: List<RecordLabel> = emptyList(),
    ) : AlbumUiState {
        /** All tracks in play order, the queue Play and Shuffle start. */
        val tracks: List<Track> get() = discs.flatMap { it.tracks }

        /** Counted from the tracks shown, so the header always matches the list. */
        val trackCount: Int get() = discs.sumOf { it.tracks.size }

        val duration: Duration get() = tracks.fold(Duration.ZERO) { total, track -> total + track.duration }

        /** Disc headers only when the album has more than one disc. */
        val showDiscHeaders: Boolean get() = discs.size > 1

        /** The title without its edition, and the edition as the version line (#132). */
        val title: AlbumTitle get() = albumTitle(album.title)

        /** The best quality among the tracks, when the setting shows it. */
        val quality: QualityLabel? get() = if (page.showQuality) albumQuality(tracks) else null

        val showRating: Boolean get() = page.showRatingBar && ratingMode != RatingMode.Off

        /** The original files' total size; `null` when no track's size is known. */
        val sizeBytes: Long? get() = tracks.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum()

        /** How often its tracks were played: the server keeps no album count of its own. */
        val playCount: Int get() = tracks.sumOf { it.userData.playCount }

        /** About has an overview or at least one fact row. */
        private val hasAbout: Boolean
            get() =
                !album.overview.isNullOrBlank() || genres.isNotEmpty() || labels.isNotEmpty() ||
                    album.releaseDate != null || album.dateAdded != null || playCount > 0 || sizeBytes != null

        /** The sections that show, in the chosen order; empty ones are left out. */
        val sections: List<AlbumSection>
            get() =
                page.sectionOrder.filter {
                    when (it) {
                        AlbumSection.Tracks -> true
                        AlbumSection.MoreFromArtist -> page.showMoreFromArtist && moreAlbums.isNotEmpty()
                        AlbumSection.About -> page.showAbout && hasAbout
                    }
                }

        fun shows(button: AlbumButton): Boolean = button !in page.hiddenButtons

        /** A track's artists show when the setting says so or when they differ from the album's. */
        fun showsArtists(track: Track): Boolean =
            page.alwaysShowTrackArtists || track.artists.map { it.id } != album.albumArtists.map { it.id }
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

    /** Continue where the album was left (#132). */
    data object Resume : AlbumAction

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

    /** Rate the album with [halfStars], 1-10; `null` clears the rating. */
    data class Rate(
        val halfStars: Int?,
    ) : AlbumAction

    /** Download one track, or the whole album when [trackId] is `null`. */
    data class Download(
        val trackId: MediaId? = null,
    ) : AlbumAction

    /** A swipe on a track to the right (or left): the action chosen in Settings -> Media lists (#123). */
    data class SwipeTrack(
        val track: Track,
        val right: Boolean,
    ) : AlbumAction

    /** Rates a track (a Rate swipe): [halfStars] 1-10, `null` clears. */
    data class RateTrack(
        val track: Track,
        val halfStars: Int?,
    ) : AlbumAction

    /** A long press starts selecting tracks (#123); while selecting a tap toggles one. */
    data class ToggleSelection(
        val id: MediaId,
    ) : AlbumAction

    data object SelectAll : AlbumAction

    /** Clear or Back: selecting ends. */
    data object ClearSelection : AlbumAction

    /** A selection toolbar action on the selected tracks (Add to playlist is the screen's); selecting ends. */
    data class OnSelection(
        val command: SelectionCommand,
    ) : AlbumAction
}
