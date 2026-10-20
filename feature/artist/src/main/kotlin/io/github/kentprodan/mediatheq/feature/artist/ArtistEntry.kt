package io.github.kentprodan.mediatheq.feature.artist

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the artist page. */
fun EntryProviderScope<NavKey>.artistEntry(navigator: Navigator) {
    entry<ArtistKey> { key ->
        ArtistRoute(key.id, onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) }, onBack = { navigator.back() })
    }
}
