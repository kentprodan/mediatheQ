package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumArtistsKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumsKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.GenreKey
import io.github.kentprodan.mediatheq.core.navigation.GenresKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryListKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.PlaylistsKey
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelsKey
import io.github.kentprodan.mediatheq.core.navigation.TracksKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey

/**
 * Registers the Library hub (#129), the lists it opens (with Back), a year's albums, and the lists that are navigation
 * entries of their own (#122, no Back); [onOpenDownloads] opens Downloads.
 */
fun EntryProviderScope<NavKey>.libraryEntry(
    navigator: Navigator,
    onOpenDownloads: () -> Unit,
) {
    @Composable
    fun Library(
        filter: LibraryFilter,
        inTab: Boolean,
        yearPage: YearKey? = null,
        labelPage: RecordLabelKey? = null,
    ) = LibraryRoute(
        onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
        onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
        onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
        onNewPlaylist = { navigator.navigateTo(AddToPlaylistKey(emptyList())) },
        onAddToPlaylist = { navigator.navigateTo(AddToPlaylistKey(it)) },
        onOpenDownloads = onOpenDownloads,
        filter = filter,
        onBack = if (inTab) ({ navigator.back() }) else null,
        onOpenYear = { navigator.navigateTo(it) },
        onOpenGenre = { navigator.navigateTo(GenreKey(it)) },
        onOpenLabel = { navigator.navigateTo(it) },
        yearPage = yearPage,
        labelPage = labelPage,
    )
    entry<LibraryKey> { LibraryHubRoute(onOpen = { navigator.navigateTo(LibraryListKey(it)) }) }
    entry<LibraryListKey> { Library(it.shortcut.filter, inTab = true) }
    entry<YearKey> { Library(LibraryFilter.Albums, inTab = true, yearPage = it) }
    entry<RecordLabelKey> { Library(LibraryFilter.Albums, inTab = true, labelPage = it) }
    entry<AlbumsKey> { Library(LibraryFilter.Albums, inTab = false) }
    entry<AlbumArtistsKey> { Library(LibraryFilter.Artists, inTab = false) }
    entry<PlaylistsKey> { Library(LibraryFilter.Playlists, inTab = false) }
    entry<TracksKey> { Library(LibraryFilter.Songs, inTab = false) }
    entry<GenresKey> { Library(LibraryFilter.Genres, inTab = false) }
    entry<RecordLabelsKey> { Library(LibraryFilter.RecordLabels, inTab = false) }
}

/** The list a hub tile opens. */
internal val LibraryShortcut.filter: LibraryFilter
    get() =
        when (this) {
            LibraryShortcut.Albums -> LibraryFilter.Albums
            LibraryShortcut.AlbumArtists -> LibraryFilter.Artists
            LibraryShortcut.Genres -> LibraryFilter.Genres
            LibraryShortcut.Tracks -> LibraryFilter.Songs
            LibraryShortcut.Playlists -> LibraryFilter.Playlists
            LibraryShortcut.Years -> LibraryFilter.Years
            LibraryShortcut.RecordLabels -> LibraryFilter.RecordLabels
        }
