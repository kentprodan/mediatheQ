package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand

/**
 * One Library list (Albums, Album artists, Tracks, Playlists; opened from the hub, #129, or as a navigation entry,
 * #122). The header shows in every state, so this wraps [content] instead of being a sealed type itself.
 */
data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter.Albums,
    /** Set on a year's page (#129): the albums of that year. */
    val yearPage: YearKey? = null,
    /** Set on a record label's page (#131): the albums of that label. */
    val labelPage: RecordLabelKey? = null,
    val albumColumns: Int = LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
    /** The Genres list as a grid of covers (#130). */
    val genresGrid: Boolean = false,
    /** The current filter's sort. */
    val sort: LibrarySort = LibrarySort.Name,
    val content: LibraryContent = LibraryContent.Loading,
    /** A sync is running (pull-to-refresh spinner). */
    val refreshing: Boolean = false,
    /** A failed sync while items are shown: a snackbar, acknowledged with [LibraryAction.ErrorShown]. */
    val refreshError: MqError? = null,
    /** How the list header looks (Settings -> Media lists, #123). */
    val header: MediaListPreferences = MediaListPreferences(),
    /** The header's filter and search in list. */
    val listFilter: LibraryListFilter = LibraryListFilter(),
    /** Items in the list as filtered. */
    val itemCount: Int = 0,
    /** Multi-select (#123): the selected albums, artists, playlists or songs of this tab. */
    val selection: ListSelection<MediaId> = ListSelection(),
    /** Tracks of a selection to add to a playlist: the picker opens, then [LibraryAction.AddToPlaylistShown]. */
    val addToPlaylist: List<MediaId>? = null,
)

/** What the list area shows. */
sealed interface LibraryContent {
    data object Loading : LibraryContent

    /** Synced, but the server has nothing for this tab. */
    data object Empty : LibraryContent

    /** Nothing on the phone yet and the server cannot be reached. */
    data object Offline : LibraryContent

    /** Offline mode, and nothing for this tab is downloaded (#63). */
    data object NothingDownloaded : LibraryContent

    /** The tab has items, but none passes the header's filter or search (#123). */
    data object NoMatches : LibraryContent

    data class Error(
        val error: MqError,
    ) : LibraryContent

    data class Albums(
        val albums: List<Album>,
    ) : LibraryContent

    data class Artists(
        val artists: List<Artist>,
    ) : LibraryContent

    data class Playlists(
        val playlists: List<Playlist>,
    ) : LibraryContent

    data class Songs(
        val tracks: List<Track>,
    ) : LibraryContent

    /** The genres (#130), with their counts and pictures. */
    data class Genres(
        val genres: List<Genre>,
    ) : LibraryContent

    /** The record labels (#131), with their album counts and pictures. */
    data class RecordLabels(
        val labels: List<RecordLabel>,
    ) : LibraryContent

    /** The years of the albums (#129), each with its albums. */
    data class Years(
        val years: List<YearAlbums>,
    ) : LibraryContent
}

sealed interface LibraryAction {
    /** Sort for the current filter. */
    data class SelectSort(
        val sort: LibrarySort,
    ) : LibraryAction

    data class SetAlbumColumns(
        val columns: Int,
    ) : LibraryAction

    /** The Genres list as a grid, or rows (#130). */
    data class SetGenresGrid(
        val on: Boolean,
    ) : LibraryAction

    /** Pull to refresh, or Retry on an error state. */
    data object Refresh : LibraryAction

    data object ErrorShown : LibraryAction

    /** A tap on a song: the default track action (#122), with the shown songs as the list. */
    data class PlayTrack(
        val track: Track,
    ) : LibraryAction

    /** Queue the song right after the playing one. */
    data class PlayNext(
        val track: Track,
    ) : LibraryAction

    data class Download(
        val track: Track,
    ) : LibraryAction

    /** Queue the song at the end. */
    data class AddToQueue(
        val track: Track,
    ) : LibraryAction

    data class SetFavorite(
        val track: Track,
        val isFavorite: Boolean,
    ) : LibraryAction

    /** The header's Favorites only filter. */
    data class SetFavoritesOnly(
        val on: Boolean,
    ) : LibraryAction

    /** The header's Downloaded only filter. */
    data class SetDownloadedOnly(
        val on: Boolean,
    ) : LibraryAction

    /** Opens search in list, or with `null` closes it (and drops its text). */
    data class Search(
        val text: String?,
    ) : LibraryAction

    /** A long press starts selecting with an item; while selecting a tap toggles one. */
    data class ToggleSelection(
        val id: MediaId,
    ) : LibraryAction

    data object SelectAll : LibraryAction

    /** Clear or Back: selecting ends. */
    data object ClearSelection : LibraryAction

    /** A selection toolbar action on the selected items (by their tracks); selecting ends. */
    data class OnSelection(
        val command: SelectionCommand,
    ) : LibraryAction

    data object AddToPlaylistShown : LibraryAction

    /** A top button of the header: acts on the whole list as filtered and sorted. */
    data class Top(
        val button: TopButton,
    ) : LibraryAction

    /** A swipe on a song to the right (or left): the action chosen in Settings -> Media lists (#123). */
    data class SwipeTrack(
        val track: Track,
        val right: Boolean,
    ) : LibraryAction

    /** [halfStars] 1-10, `null` clears the rating. */
    data class RateTrack(
        val track: Track,
        val halfStars: Int?,
    ) : LibraryAction
}
