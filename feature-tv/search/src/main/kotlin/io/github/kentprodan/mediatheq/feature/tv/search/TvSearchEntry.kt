package io.github.kentprodan.mediatheq.feature.tv.search

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey

/** Registers Search on TV (#244), a drawer destination; results open the album, artist and playlist pages. */
fun EntryProviderScope<NavKey>.tvSearchEntry(navigator: Navigator) {
    entry<SearchKey> {
        TvSearchRoute(
            TvSearchNavigation(
                onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
                onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
                onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
            ),
        )
    }
}
