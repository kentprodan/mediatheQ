package io.github.kentprodan.mediatheq.feature.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.SettingsKey

/** Registers the Home tab root; [onOpenDownloads] switches to the Downloads tab. */
fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
    onOpenDownloads: () -> Unit,
) {
    entry<HomeKey> {
        HomeRoute(
            HomeNavigation(
                onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
                onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
                onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
                onOpenSettings = { navigator.navigateTo(SettingsKey) },
                onOpenDownloads = onOpenDownloads,
            ),
        )
    }
}
