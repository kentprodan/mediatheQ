package io.github.kentprodan.mediatheq.feature.tv.artist

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the artist page on TV (#239). */
fun EntryProviderScope<NavKey>.tvArtistEntry(navigator: Navigator) {
    entry<ArtistKey> { TvArtistRoute(it.id, onOpenAlbum = { id -> navigator.navigateTo(AlbumKey(id)) }) }
}
