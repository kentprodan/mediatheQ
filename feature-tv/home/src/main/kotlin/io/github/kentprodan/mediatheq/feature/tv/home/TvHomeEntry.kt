package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey

/** Registers Home on TV (#236); album, artist and playlist pages are their own entries. */
fun EntryProviderScope<NavKey>.tvHomeEntry(navigator: Navigator) {
    entry<HomeKey> {
        TvHomeRoute(
            TvHomeNavigation(
                onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
                onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
                onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
            ),
        )
    }
}
