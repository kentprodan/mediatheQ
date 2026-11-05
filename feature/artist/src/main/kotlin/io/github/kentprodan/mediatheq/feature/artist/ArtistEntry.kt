package io.github.kentprodan.mediatheq.feature.artist

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistTopTracksKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the artist page and its "Show all" top tracks. */
fun EntryProviderScope<NavKey>.artistEntry(navigator: Navigator) {
    entry<ArtistKey> { key ->
        ArtistRoute(
            key.id,
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenTopTracks = { navigator.navigateTo(ArtistTopTracksKey(key.id)) },
            onBack = { navigator.back() },
            onAddToPlaylist = { navigator.navigateTo(AddToPlaylistKey(it)) },
        )
    }
    entry<ArtistTopTracksKey> { key ->
        ArtistTopTracksRoute(
            key.id,
            onBack = { navigator.back() },
            onAddToPlaylist = { navigator.navigateTo(AddToPlaylistKey(it)) },
        )
    }
}
