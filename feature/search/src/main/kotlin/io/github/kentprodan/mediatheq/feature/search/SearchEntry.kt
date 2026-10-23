package io.github.kentprodan.mediatheq.feature.search

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey

/** Registers the Search tab root. */
fun EntryProviderScope<NavKey>.searchEntry(navigator: Navigator) {
    entry<SearchKey> {
        SearchRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
        )
    }
}
