package io.github.kentprodan.mediatheq.feature.tv.album

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey

/** Registers the album and playlist pages on TV (#238). */
fun EntryProviderScope<NavKey>.tvAlbumEntries() {
    entry<AlbumKey> { TvAlbumRoute(it.id) }
    entry<PlaylistKey> { TvPlaylistRoute(it.id) }
}
