package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * The Library tab. The filter bar and top bar show in every state, so this wraps [content] instead of being a sealed
 * type itself.
 */
data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter.Albums,
    val filterStyle: FilterControlStyle = FilterControlStyle.Chips,
    val albumColumns: Int = LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
    /** The current filter's sort. */
    val sort: LibrarySort = LibrarySort.Name,
    val content: LibraryContent = LibraryContent.Loading,
    /** A sync is running (pull-to-refresh spinner). */
    val refreshing: Boolean = false,
    /** A failed sync while items are shown: a snackbar, acknowledged with [LibraryAction.ErrorShown]. */
    val refreshError: MqError? = null,
)

/** What the list area shows. */
sealed interface LibraryContent {
    data object Loading : LibraryContent

    /** Synced, but the server has nothing for this tab. */
    data object Empty : LibraryContent

    /** Nothing on the phone yet and the server cannot be reached. */
    data object Offline : LibraryContent

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
}

sealed interface LibraryAction {
    data class SelectFilter(
        val filter: LibraryFilter,
    ) : LibraryAction

    /** Sort for the current filter. */
    data class SelectSort(
        val sort: LibrarySort,
    ) : LibraryAction

    data class SetAlbumColumns(
        val columns: Int,
    ) : LibraryAction

    data class SetFilterStyle(
        val style: FilterControlStyle,
    ) : LibraryAction

    /** Pull to refresh, or Retry on an error state. */
    data object Refresh : LibraryAction

    data object ErrorShown : LibraryAction

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
}
