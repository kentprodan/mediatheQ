package io.github.kentprodan.mediatheq.feature.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the Home tab root. [onOpenShowcase] opens the theme showcase, which lives in the app. */
fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
    onLogout: () -> Unit,
    onOpenShowcase: () -> Unit,
) {
    entry<HomeKey> {
        HomeRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onLogout = onLogout,
            onOpenShowcase = onOpenShowcase,
        )
    }
}
