package io.github.kentprodan.mediatheq.feature.playlist

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey

/** Registers the playlist page. */
fun EntryProviderScope<NavKey>.playlistEntry(navigator: Navigator) {
    entry<PlaylistKey> { key -> PlaylistRoute(key.id, onBack = { navigator.back() }) }
}
