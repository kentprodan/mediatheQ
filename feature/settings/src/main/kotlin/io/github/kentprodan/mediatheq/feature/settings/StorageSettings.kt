package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AutoOffline
import io.github.kentprodan.mediatheq.core.data.ImagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

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
internal enum class StorageDialog { AutoOffline, DownloadQuality }

/** General: offline mode and when it turns on by itself. */
internal fun LazyListScope.storageGeneralGroups(
    library: LibraryPreferences,
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
    restoreDefaultsGroup(onRestore)
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
}

/** [StorageDialogHost]'s content, for screenshot tests. */
@Composable
internal fun StorageDialogContent(
    dialog: StorageDialog,
    audio: AudioPreferences,
    library: LibraryPreferences,
) = when (dialog) {
    StorageDialog.AutoOffline -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_auto_offline),
            AutoOffline.entries,
            library.autoOffline,
        ) { stringResource(it.label) }
    }

    StorageDialog.DownloadQuality -> {
        ChoiceDialogContent(
            stringResource(R.string.settings_st_download_quality),
            AudioPreferences.DOWNLOAD_QUALITIES,
            audio.downloadQuality,
        ) { it.label() }
    }
}

private val AutoOffline.label: Int
    get() =
        when (this) {
            AutoOffline.NoNetwork -> R.string.settings_st_auto_no_network
            AutoOffline.NotOnWifi -> R.string.settings_st_auto_not_wifi
            AutoOffline.Never -> R.string.settings_st_auto_never
        }
