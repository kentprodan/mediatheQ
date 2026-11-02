package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumArtistsKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumsKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.PlaylistsKey
import io.github.kentprodan.mediatheq.core.navigation.TracksKey

/**
 * Registers the Library tab root and its lists as navigation entries of their own (#122); [onOpenDownloads] opens
 * Downloads.
 */
fun EntryProviderScope<NavKey>.libraryEntry(
    navigator: Navigator,
    onOpenDownloads: () -> Unit,
) {
    @Composable
    fun Library(fixedFilter: LibraryFilter?) =
        LibraryRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
            onNewPlaylist = { navigator.navigateTo(AddToPlaylistKey(emptyList())) },
            onOpenDownloads = onOpenDownloads,
            fixedFilter = fixedFilter,
        )
    entry<LibraryKey> { Library(null) }
    entry<AlbumsKey> { Library(LibraryFilter.Albums) }
    entry<AlbumArtistsKey> { Library(LibraryFilter.Artists) }
    entry<PlaylistsKey> { Library(LibraryFilter.Playlists) }
    entry<TracksKey> { Library(LibraryFilter.Songs) }
}
