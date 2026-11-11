package io.github.kentprodan.mediatheq.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AutoDownloads
import io.github.kentprodan.mediatheq.core.data.AutoOffline
import io.github.kentprodan.mediatheq.core.data.ImagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.StorageLocation
import io.github.kentprodan.mediatheq.core.data.StorageMove
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Settings -> Offline, cache, and download (#144): a hub of four pages. */
internal fun LazyListScope.storageHubGroups(onOpen: (SettingsPage) -> Unit) {
    item(key = "storage_hub") {
        Group(R.string.settings_storage_page) {
            item { shapes ->
                HubRow(shapes, R.string.settings_st_general, R.string.settings_st_general_summary, MqIcons.CloudOff) {
                    onOpen(SettingsPage.StorageGeneral)
                }
            }
            item { shapes ->
                HubRow(shapes, R.string.settings_st_media, R.string.settings_st_media_summary, MqIcons.Download) {
                    onOpen(SettingsPage.StorageMedia)
                }
            }
            item { shapes ->
                HubRow(
                    shapes,
                    R.string.settings_st_playback,
                    R.string.settings_st_playback_summary,
                    MqIcons.PlayArrow,
                ) {
                    onOpen(SettingsPage.StoragePlayback)
                }
            }
            item { shapes ->
                HubRow(shapes, R.string.settings_st_images, R.string.settings_st_images_summary, MqIcons.GridView) {
                    onOpen(SettingsPage.StorageImages)
                }
            }
        }
    }
}

/** The pages' choice dialogs. */
internal enum class StorageDialog { AutoOffline, DownloadQuality, AutoDownloads, Location }

/** General: offline mode and when it turns on by itself; where downloads live, with a removable volume (#146). */
internal fun LazyListScope.storageGeneralGroups(
    library: LibraryPreferences,
    locations: List<StorageLocation>,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (StorageDialog) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "offline") {
        Group(R.string.settings_st_offline) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_offline_mode),
                    stringResource(R.string.settings_offline_mode_summary),
                    checked = library.offlineMode,
                    onCheckedChange = { onAction(SettingsAction.SetOfflineMode(it)) },
                )
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_st_auto_offline),
                    stringResource(library.autoOffline.label),
                ) { onOpenDialog(StorageDialog.AutoOffline) }
            }
        }
    }
    if (locations.isNotEmpty()) {
        item(key = "location") {
            Group(R.string.settings_st_storage) {
                item { shapes ->
                    DialogItem(
                        shapes,
                        stringResource(R.string.settings_st_location),
                        locations.firstOrNull { it.current }?.label()
                            ?: stringResource(R.string.settings_st_location_missing),
                    ) { onOpenDialog(StorageDialog.Location) }
                }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** "SD card · 12 GB free". */
@Composable
private fun StorageLocation.label(): String =
    stringResource(
        R.string.settings_st_location_free,
        name ?: stringResource(R.string.settings_st_internal),
        Formatter.formatShortFileSize(LocalContext.current, freeBytes),
    )

/** The move (#146): progress while copying (it cannot be stopped), then the restart; or why it failed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveDownloadsDialog(
    move: StorageMove,
    onDismiss: () -> Unit,
) {
    when (move) {
        StorageMove.Idle -> {
            Unit
        }

        is StorageMove.Failed -> {
            MqDialog(
                title = stringResource(R.string.settings_st_move_failed),
                confirmLabel = null,
                onConfirm = {},
                dismissLabel = stringResource(R.string.settings_ok),
                onDismiss = onDismiss,
            ) { Text(stringResource(move.reason)) }
        }

        else -> {
            BasicAlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) { MoveProgressContent(move) }
        }
    }
}

private val StorageMove.Failed.reason: Int
    get() = if (noSpace) R.string.settings_st_move_no_space else R.string.settings_st_move_error

/** The copy's progress, then the restart; on its own for screenshot tests (dialog windows render blank there). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MoveProgressContent(move: StorageMove) {
    Surface(shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor) {
        Column(Modifier.padding(MediatheqSpacing.xl), verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l)) {
            Text(stringResource(R.string.settings_st_moving), style = MaterialTheme.typography.headlineSmall)
            if (move is StorageMove.Moving) {
                LinearWavyProgressIndicator(progress = { move.fraction }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.settings_st_moving_summary))
            } else {
                Text(stringResource(R.string.settings_st_restarting))
            }
        }
    }
}

/** Media: when, how and how many tracks download. */
internal fun LazyListScope.storageMediaGroups(
    audio: AudioPreferences,
    onAction: (SettingsAction) -> Unit,
    onOpenDialog: (StorageDialog) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "downloads") {
        Group(R.string.settings_st_downloads) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_downloads_unmetered),
                    stringResource(R.string.settings_downloads_unmetered_summary),
                    checked = audio.downloadsUnmeteredOnly,
                    onCheckedChange = { onAction(SettingsAction.SetDownloadsUnmeteredOnly(it)) },
                )
            }
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_st_download_quality),
                    audio.downloadQuality.label(),
                ) { onOpenDialog(StorageDialog.DownloadQuality) }
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_st_parallel),
                    stringResource(R.string.settings_st_parallel_summary),
                    AudioPreferences.PARALLEL_DOWNLOADS.toList(),
                    selected = audio.maxParallelDownloads,
                    label = { it.toString() },
                    onSelect = { onAction(SettingsAction.SetMaxParallelDownloads(it)) },
                )
            }
        }
    }
    item(key = "auto_downloads") {
        Group(R.string.settings_st_favorites) {
            item { shapes ->
                DialogItem(
                    shapes,
                    stringResource(R.string.settings_st_auto_downloads),
                    stringResource(audio.autoDownloads.label),
                ) { onOpenDialog(StorageDialog.AutoDownloads) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_st_remove_unfavorited),
                    stringResource(R.string.settings_st_remove_unfavorited_summary),
                    audio.removeUnfavorited,
                    enabled = audio.autoDownloads != AutoDownloads.Disabled,
                ) { onAction(SettingsAction.SetRemoveUnfavorited(it)) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** Playback: the streaming cache. */
internal fun LazyListScope.storagePlaybackGroups(
    audio: AudioPreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    item(key = "cache_intro") {
        Text(
            stringResource(R.string.settings_st_cache_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "cache") {
        Group(R.string.settings_st_cache) {
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_cache),
                    null,
                    AudioPreferences.CACHE_SIZES_GB,
                    selected = audio.streamingCacheGb,
                    label = { stringResource(R.string.settings_cache_gb, it) },
                    onSelect = { onAction(SettingsAction.SetStreamingCacheGb(it)) },
                )
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** Images: where covers are kept, when they load, how large. */
internal fun LazyListScope.storageImagesGroups(
    images: ImagePreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    fun set(changed: ImagePreferences) = onAction(SettingsAction.SetImages(changed))
    item(key = "images") {
        Group(R.string.settings_st_covers) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_st_persistent),
                    stringResource(R.string.settings_st_persistent_summary),
                    images.persistentCache,
                ) { set(images.copy(persistentCache = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_st_images_wifi),
                    stringResource(R.string.settings_st_images_wifi_summary),
                    images.wifiOnly,
                ) { set(images.copy(wifiOnly = it)) }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_st_hq),
                    stringResource(R.string.settings_st_hq_summary),
                    images.highQuality,
                ) { set(images.copy(highQuality = it)) }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

@Composable
internal fun StorageDialogHost(
    dialog: StorageDialog,
    audio: AudioPreferences,
    library: LibraryPreferences,
    locations: List<StorageLocation>,
    onAction: (SettingsAction) -> Unit,
    onDismiss: () -> Unit,
) = when (dialog) {
    StorageDialog.AutoOffline -> {
        ChoiceDialog(
            stringResource(R.string.settings_st_auto_offline),
            AutoOffline.entries,
            library.autoOffline,
            { stringResource(it.label) },
            { onAction(SettingsAction.SetAutoOffline(it)) },
            onDismiss,
        )
    }

    StorageDialog.AutoDownloads -> {
        ChoiceDialog(
            stringResource(R.string.settings_st_auto_downloads),
            AutoDownloads.entries,
            audio.autoDownloads,
            { stringResource(it.label) },
            { onAction(SettingsAction.SetAutoDownloads(it)) },
            onDismiss,
        )
    }

    StorageDialog.DownloadQuality -> {
        ChoiceDialog(
            stringResource(R.string.settings_st_download_quality),
            AudioPreferences.DOWNLOAD_QUALITIES,
            audio.downloadQuality,
            { it.label() },
            { onAction(SettingsAction.SetDownloadQuality(it)) },
            onDismiss,
        )
    }

    StorageDialog.Location -> {
        ChoiceDialog(
            stringResource(R.string.settings_st_location),
            locations,
            locations.firstOrNull { it.current },
            { it?.label().orEmpty() },
            { if (it?.current == false) onAction(SettingsAction.MoveDownloads(it)) },
            onDismiss,
        )
    }
}

/** [StorageDialogHost]'s content, for screenshot tests. */
@Composable
internal fun StorageDialogContent(
    dialog: StorageDialog,
    audio: AudioPreferences,
    library: LibraryPreferences,
    locations: List<StorageLocation> = emptyList(),
) = when (dialog) {
    StorageDialog.AutoOffline -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_auto_offline),
            AutoOffline.entries,
            library.autoOffline,
        ) { stringResource(it.label) }
    }

    StorageDialog.AutoDownloads -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_auto_downloads),
            AutoDownloads.entries,
            audio.autoDownloads,
        ) { stringResource(it.label) }
    }

    StorageDialog.DownloadQuality -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_download_quality),
            AudioPreferences.DOWNLOAD_QUALITIES,
            audio.downloadQuality,
        ) { it.label() }
    }

    StorageDialog.Location -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_location),
            locations,
            locations.firstOrNull { it.current },
        ) { it?.label().orEmpty() }
    }
}

private val AutoDownloads.label: Int
    get() =
        when (this) {
            AutoDownloads.Disabled -> R.string.settings_st_auto_dl_disabled
            AutoDownloads.Tracks -> R.string.settings_st_auto_dl_tracks
            AutoDownloads.TracksAndAlbums -> R.string.settings_st_auto_dl_albums
            AutoDownloads.TracksAlbumsAndPlaylists -> R.string.settings_st_auto_dl_playlists
        }

private val AutoOffline.label: Int
    get() =
        when (this) {
            AutoOffline.NoNetwork -> R.string.settings_st_auto_no_network
            AutoOffline.NotOnWifi -> R.string.settings_st_auto_not_wifi
            AutoOffline.Never -> R.string.settings_st_auto_never
        }
