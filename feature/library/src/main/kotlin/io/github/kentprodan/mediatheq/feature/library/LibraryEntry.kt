package io.github.kentprodan.mediatheq.feature.library

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey

/** Registers the Library tab root. */
fun EntryProviderScope<NavKey>.libraryEntry(navigator: Navigator) {
    entry<LibraryKey> {
        LibraryRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
        )
    }
}
