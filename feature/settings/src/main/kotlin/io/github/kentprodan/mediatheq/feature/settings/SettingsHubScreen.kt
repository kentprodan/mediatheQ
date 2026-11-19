package io.github.kentprodan.mediatheq.feature.settings

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

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
    onBack: () -> Unit,
) {
    SettingsHubScreen(onOpen = onOpen, onBack = onBack)
}

/** Settings: groups of categories (segmented rows with an icon); a row opens its page. Stateless. */
@Composable
fun SettingsHubScreen(
    onOpen: (SettingsHubEntry) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.settings_title), onBack) {
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
