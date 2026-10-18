package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.Composable
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

// shortcut: placeholder screens until the real features exist (#10, #17, #19, #20); delete with the last of them.

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
fun placeholderEntries(navigator: Navigator): (NavKey) -> NavEntry<NavKey> =
    entryProvider {
        entry<HomeKey> {
            Placeholder("Home", "Open theme showcase") { navigator.navigateTo(ThemeShowcaseKey) }
        }
        entry<LibraryKey> { PlaceholderRoot("Library", navigator) }
        entry<SearchKey> { PlaceholderRoot("Search", navigator) }
        entry<DownloadsKey> { PlaceholderRoot("Downloads", navigator) }
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
