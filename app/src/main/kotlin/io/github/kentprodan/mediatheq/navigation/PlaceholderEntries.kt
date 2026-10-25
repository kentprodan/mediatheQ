package io.github.kentprodan.mediatheq.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.preview.ThemeShowcase
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import kotlinx.serialization.Serializable

// shortcut: placeholder screens until the real features exist (#19); delete with the last of them.

/** A placeholder page [depth] levels deep inside a tab, to exercise the per-tab back stacks. */
@Serializable
data class PlaceholderDetailKey(
    val title: String,
    val depth: Int,
) : NavKey

/** The theme showcase, reachable from Home for manual checks of the design system. */
@Serializable
data object ThemeShowcaseKey : NavKey

/** Entries for the tab roots and placeholder pages; real features replace them one by one. */
fun placeholderEntries(
    navigator: Navigator,
    onLogout: () -> Unit = {},
): (NavKey) -> NavEntry<NavKey> = entryProvider { placeholders(navigator, onLogout) }

/**
 * Placeholder entries. [includeLibrary], [includeSearch] and [includeDownloads] are false in the app, where the real
 * Library (#10), Search (#17) and Downloads (#20) tabs are registered; the shell tests keep the placeholders to exercise back stacks without Hilt.
 */
fun EntryProviderScope<NavKey>.placeholders(
    navigator: Navigator,
    onLogout: () -> Unit = {},
    includeLibrary: Boolean = true,
    includeSearch: Boolean = true,
    includeDownloads: Boolean = true,
) {
    entry<HomeKey> {
        Box {
            Placeholder("Home", "Open theme showcase") { navigator.navigateTo(ThemeShowcaseKey) }
            // shortcut: logout lives here until the Settings screen exists (#25).
            TextButton(onClick = onLogout, Modifier.align(Alignment.BottomCenter)) { Text("Log out") }
        }
    }
    if (includeLibrary) entry<LibraryKey> { PlaceholderRoot("Library", navigator) }
    if (includeSearch) entry<SearchKey> { PlaceholderRoot("Search", navigator) }
    if (includeDownloads) entry<DownloadsKey> { PlaceholderRoot("Downloads", navigator) }
    entry<PlaceholderDetailKey> { key ->
        Placeholder("${key.title} ${key.depth}", "Open next") {
            navigator.navigateTo(PlaceholderDetailKey(key.title, key.depth + 1))
        }
    }
    entry<ThemeShowcaseKey> { ThemeShowcase() }
}

@Composable
private fun PlaceholderRoot(
    title: String,
    navigator: Navigator,
) {
    Placeholder(title, "Open detail") { navigator.navigateTo(PlaceholderDetailKey(title, 1)) }
}

@Composable
private fun Placeholder(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    MqEmptyState(
        icon = MqIcons.MusicNote,
        title = title,
        body = "Coming soon",
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
