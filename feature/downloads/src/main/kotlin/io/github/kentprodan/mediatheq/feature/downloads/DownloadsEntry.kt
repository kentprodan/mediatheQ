package io.github.kentprodan.mediatheq.feature.downloads

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.StorageMediaSettingsKey

/**
 * Registers Downloads; its albums and playlists open on the same stack. When it is not a navigation tab ([isTab],
 * #122) it opens over another page and shows a back button.
 */
fun EntryProviderScope<NavKey>.downloadsEntry(
    navigator: Navigator,
    isTab: () -> Boolean = { true },
) {
    entry<DownloadsKey> {
        DownloadsRoute(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
            onOpenRules = { navigator.navigateTo(StorageMediaSettingsKey) },
            onBack = if (isTab()) null else ({ navigator.back() }),
        )
    }
}
