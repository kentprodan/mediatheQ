package io.github.kentprodan.mediatheq.feature.settings

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/** An entry of the Settings hub; each opens its own page. An entry is added when its page has content (#120). */
enum class SettingsHubEntry(
    @StringRes val title: Int,
) {
    Interface(R.string.settings_interface),
    Playback(R.string.settings_playback),
    Storage(R.string.settings_storage_page),
    Advanced(R.string.settings_advanced),
    ManageOfflineFiles(R.string.settings_manage_offline),
    Providers(R.string.settings_providers),
    SyncManager(R.string.settings_sync_manager),
    About(R.string.settings_about),
}

internal val hubGroups =
    listOf(
        R.string.settings_title to
            listOf(
                SettingsHubEntry.Interface,
                SettingsHubEntry.Playback,
                SettingsHubEntry.Storage,
                SettingsHubEntry.Advanced,
            ),
        R.string.settings_group_misc to
            listOf(
                SettingsHubEntry.ManageOfflineFiles,
                SettingsHubEntry.Providers,
                SettingsHubEntry.SyncManager,
            ),
        R.string.settings_about to listOf(SettingsHubEntry.About),
    )

/** The hub; Sync manager opens as a sheet here. */
@Composable
internal fun SettingsHubRoute(
    onOpen: (SettingsHubEntry) -> Unit,
    onBack: () -> Unit,
) {
    var syncManager by rememberSaveable { mutableStateOf(false) }
    SettingsHubScreen(
        onOpen = { if (it == SettingsHubEntry.SyncManager) syncManager = true else onOpen(it) },
        onBack = onBack,
    )
    if (syncManager) SyncManagerSheet(onDismiss = { syncManager = false })
}

/**
 * Settings: groups of categories (segmented rows with an icon); a row opens its page. Stateless. "Manage offline files"
 * opens Downloads with a back button, also when Downloads is a navigation entry (#149).
 */
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
            SettingsHubEntry.ManageOfflineFiles -> MqIcons.Download
            SettingsHubEntry.Providers -> MqIcons.Devices
            SettingsHubEntry.SyncManager -> MqIcons.Sync
            SettingsHubEntry.About -> MqIcons.Info
        }
