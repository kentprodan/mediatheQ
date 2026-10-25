package io.github.kentprodan.mediatheq.navigation

import androidx.compose.runtime.Composable
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

// Placeholder tab roots exercise the per-tab back stacks in the shell tests; the app registers the real tabs.

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
fun placeholderEntries(navigator: Navigator): (NavKey) -> NavEntry<NavKey> = entryProvider { placeholders(navigator) }

/**
 * Placeholder entries. [includeHome], [includeLibrary], [includeSearch] and [includeDownloads] are false in the app,
 * where the real Home (#19), Library (#10), Search (#17) and Downloads (#20) tabs are registered; the shell tests keep
 * the placeholders to exercise back stacks without Hilt. The theme showcase entry is registered in both.
 */
fun EntryProviderScope<NavKey>.placeholders(
    navigator: Navigator,
    includeHome: Boolean = true,
    includeLibrary: Boolean = true,
    includeSearch: Boolean = true,
    includeDownloads: Boolean = true,
) {
    if (includeHome) {
        entry<HomeKey> { Placeholder("Home", "Open theme showcase") { navigator.navigateTo(ThemeShowcaseKey) } }
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
