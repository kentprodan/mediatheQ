package io.github.kentprodan.mediatheq.feature.downloads

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey

/** Registers the Downloads tab root; its albums and playlists open on the tab's own stack. */
fun EntryProviderScope<NavKey>.downloadsEntry(navigator: Navigator) {
    entry<DownloadsKey> {
        DownloadsRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
        )
    }
}
