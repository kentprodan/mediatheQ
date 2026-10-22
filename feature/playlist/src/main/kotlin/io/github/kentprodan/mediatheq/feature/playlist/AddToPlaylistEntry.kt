package io.github.kentprodan.mediatheq.feature.playlist

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the "Add to playlist" dialog; the host's NavDisplay needs a [DialogSceneStrategy]. */
fun EntryProviderScope<NavKey>.addToPlaylistEntry(navigator: Navigator) {
    entry<AddToPlaylistKey>(metadata = DialogSceneStrategy.dialog()) { key ->
        AddToPlaylistRoute(key.trackIds, onClose = { navigator.back() })
    }
}
