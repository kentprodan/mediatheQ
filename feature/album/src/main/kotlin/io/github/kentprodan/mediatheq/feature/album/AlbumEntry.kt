package io.github.kentprodan.mediatheq.feature.album

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the album page. */
fun EntryProviderScope<NavKey>.albumEntry(navigator: Navigator) {
    entry<AlbumKey> { key -> AlbumRoute(key.id, onBack = { navigator.back() }) }
}
