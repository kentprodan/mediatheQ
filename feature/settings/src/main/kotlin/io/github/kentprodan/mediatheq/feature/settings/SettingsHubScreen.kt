package io.github.kentprodan.mediatheq.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * An entry of the Settings hub; each opens its own page (#120). Settings only (#536): the servers and the sync manager
 * live under the profile on Home, offline files in Downloads, the changelog and contact support in About.
 */
enum class SettingsHubEntry(
    @StringRes val title: Int,
) {
    Interface(R.string.settings_interface),
    Playback(R.string.settings_playback),
    Storage(R.string.settings_storage_page),
    Advanced(R.string.settings_advanced),
    Backup(R.string.settings_backup),
    About(R.string.settings_about),
}

/** The settings by topic, then About on its own at the end. */
internal val hubGroups =
    listOf(
        R.string.settings_title to
            listOf(
                SettingsHubEntry.Interface,
                SettingsHubEntry.Playback,
                SettingsHubEntry.Storage,
                SettingsHubEntry.Advanced,
                SettingsHubEntry.Backup,
            ),
        R.string.settings_about to listOf(SettingsHubEntry.About),
    )

@Composable
internal fun SettingsHubRoute(
    onOpen: (SettingsHubEntry) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SettingsHubScreen(
        onOpen = onOpen,
        onBack = onBack,
        query = query,
        onQuery = { query = it },
        onOpenPage = onOpenPage,
    )
}

/**
 * Settings: a search field (#536), then groups of categories (segmented rows with an icon); a row opens its page.
 * While [query] is set, the hits replace the groups: each names a setting and its page, a tap opens the page.
 * Stateless.
 */
@Composable
fun SettingsHubScreen(
    onOpen: (SettingsHubEntry) -> Unit,
    onBack: () -> Unit,
    query: String = "",
    onQuery: (String) -> Unit = {},
    onOpenPage: (SettingsPage) -> Unit = {},
) {
    val text = rememberStringReader()
    val hits = remember(query) { searchSettings(query, text) }
    SettingsScaffold(stringResource(R.string.settings_title), onBack) {
        item(key = "search") {
            SettingsSearchField(query, onQuery, Modifier.fillMaxWidth().padding(top = MediatheqSpacing.s))
        }
        if (query.isNotBlank()) {
            item(key = "hits") {
                if (hits.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_search_none),
                        Modifier.padding(top = MediatheqSpacing.l),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Group(R.string.settings_search_results) { searchHits(hits, onOpenPage) }
                }
            }
            return@SettingsScaffold
        }
        hubGroups.forEach { (title, entries) ->
            item(key = title) {
                Group(title) {
                    entries.forEach { entry ->
                        item { shapes ->
                            SegmentedListItem(
                                onClick = { onOpen(entry) },
                                shapes = shapes,
                                colors = settingsColors(),
                                // The title names the row; the icon is decoration.
                                leadingContent = { Icon(entry.icon, contentDescription = null) },
                            ) { Text(stringResource(entry.title)) }
                        }
                    }
                }
            }
        }
    }
}

private val SettingsHubEntry.icon: ImageVector
    @Composable get() =
        when (this) {
            SettingsHubEntry.Interface -> MqIcons.TouchApp
            SettingsHubEntry.Playback -> MqIcons.Radio
            SettingsHubEntry.Storage -> MqIcons.DownloadForOffline
            SettingsHubEntry.Advanced -> MqIcons.Tune
            SettingsHubEntry.Backup -> MqIcons.Restore
            SettingsHubEntry.About -> MqIcons.Info
        }
