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
    Backup(R.string.settings_backup),
    About(R.string.settings_about),
    Changelog(R.string.settings_changelog),
    ContactSupport(R.string.settings_contact),
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
                SettingsHubEntry.Backup,
            ),
        R.string.settings_about to
            listOf(SettingsHubEntry.About, SettingsHubEntry.Changelog, SettingsHubEntry.ContactSupport),
    )

/** The hub; Sync manager and Changelog open as sheets here, Contact support as a dialog. */
@Composable
internal fun SettingsHubRoute(
    info: AboutInfo,
    changelog: () -> String,
    onOpen: (SettingsHubEntry) -> Unit,
    onBack: () -> Unit,
) {
    // The entry whose sheet or dialog shows over the hub.
    var shown by rememberSaveable { mutableStateOf<SettingsHubEntry?>(null) }
    SettingsHubScreen(
        onOpen = { if (it in overHub) shown = it else onOpen(it) },
        onBack = onBack,
    )
    val dismiss = { shown = null }
    when (shown) {
        SettingsHubEntry.SyncManager -> SyncManagerSheet(onDismiss = dismiss)
        SettingsHubEntry.Changelog -> ChangelogSheet(changelog, onDismiss = dismiss)
        SettingsHubEntry.ContactSupport -> ContactSupportDialog(info, onDismiss = dismiss)
        else -> Unit
    }
}

/** The entries that open over the hub instead of a page of their own. */
private val overHub = setOf(SettingsHubEntry.SyncManager, SettingsHubEntry.Changelog, SettingsHubEntry.ContactSupport)

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
            SettingsHubEntry.Backup -> MqIcons.Restore
            SettingsHubEntry.About -> MqIcons.Info
            SettingsHubEntry.Changelog -> MqIcons.History
            SettingsHubEntry.ContactSupport -> MqIcons.Feedback
        }
