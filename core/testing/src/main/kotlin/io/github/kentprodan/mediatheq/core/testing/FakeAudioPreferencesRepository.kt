package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AudioFocusPreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.AutoActionsPreferences
import io.github.kentprodan.mediatheq.core.data.BehaviorSwitch
import io.github.kentprodan.mediatheq.core.data.HeadsetPreferences
import io.github.kentprodan.mediatheq.core.data.NotificationButtonsPreferences
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.TransitionsPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AudioPreferencesRepository] with the same rules as the real one. */
class FakeAudioPreferencesRepository : AudioPreferencesRepository {
    override val preferences = MutableStateFlow(AudioPreferences())

    override suspend fun setStreamingQuality(
        metered: Boolean,
        quality: StreamingQuality,
    ) {
        preferences.value =
            if (metered) {
                preferences.value.copy(meteredQuality = quality)
            } else {
                preferences.value.copy(unmeteredQuality = quality)
            }
    }

    override suspend fun setStreamingCacheGb(gb: Int) {
        if (gb in AudioPreferences.CACHE_SIZES_GB) preferences.value = preferences.value.copy(streamingCacheGb = gb)
    }

    override suspend fun setDownloadsUnmeteredOnly(on: Boolean) {
        preferences.value = preferences.value.copy(downloadsUnmeteredOnly = on)
    }

    override suspend fun setDownloadQuality(quality: StreamingQuality) {
        if (quality in AudioPreferences.DOWNLOAD_QUALITIES) {
            preferences.value = preferences.value.copy(downloadQuality = quality)
        }
    }

    override suspend fun setMaxParallelDownloads(count: Int) {
        if (count in AudioPreferences.PARALLEL_DOWNLOADS) {
            preferences.value = preferences.value.copy(maxParallelDownloads = count)
        }
    }

    override suspend fun setAutoDownloads(mode: io.github.kentprodan.mediatheq.core.data.AutoDownloads) {
        preferences.value = preferences.value.copy(autoDownloads = mode)
    }

    override suspend fun setRemoveUnfavorited(on: Boolean) {
        preferences.value = preferences.value.copy(removeUnfavorited = on)
    }

    override suspend fun restoreDownloadDefaults() {
        val defaults = AudioPreferences()
        preferences.value =
            preferences.value.copy(
                downloadsUnmeteredOnly = defaults.downloadsUnmeteredOnly,
                downloadQuality = defaults.downloadQuality,
                maxParallelDownloads = defaults.maxParallelDownloads,
                autoDownloads = defaults.autoDownloads,
                removeUnfavorited = defaults.removeUnfavorited,
            )
    }

    override suspend fun setForceInstantTranscoding(on: Boolean) {
        preferences.value = preferences.value.copy(forceInstantTranscoding = on)
    }

    override suspend fun restoreDecodingDefaults() {
        val defaults = AudioPreferences()
        preferences.value =
            preferences.value.copy(
                unmeteredQuality = defaults.unmeteredQuality,
                meteredQuality = defaults.meteredQuality,
                forceInstantTranscoding = defaults.forceInstantTranscoding,
            )
    }

    override suspend fun setBehavior(
        switch: BehaviorSwitch,
        on: Boolean,
    ) {
        preferences.value = preferences.value.copy(behavior = preferences.value.behavior.with(switch, on))
    }

    override suspend fun restoreBehaviorDefaults() {
        preferences.value = preferences.value.copy(behavior = PlaybackBehavior())
    }

    override suspend fun setAudioFocus(audioFocus: AudioFocusPreferences?) {
        preferences.value = preferences.value.copy(audioFocus = audioFocus ?: AudioFocusPreferences())
    }

    override suspend fun setHeadset(headset: HeadsetPreferences?) {
        preferences.value = preferences.value.copy(headset = headset ?: HeadsetPreferences())
    }

    override suspend fun setNotificationButtons(buttons: NotificationButtonsPreferences?) {
        preferences.value = preferences.value.copy(notificationButtons = buttons ?: NotificationButtonsPreferences())
    }

    override suspend fun setAutoActions(autoActions: AutoActionsPreferences?) {
        preferences.value = preferences.value.copy(autoActions = autoActions ?: AutoActionsPreferences())
    }

    override suspend fun setTransitions(transitions: TransitionsPreferences?) {
        val fades = transitions ?: TransitionsPreferences()
        preferences.value =
            preferences.value.copy(
                transitions =
                    TransitionsPreferences(
                        TransitionsPreferences.fadeMs(fades.fadeInMs),
                        TransitionsPreferences.fadeMs(fades.fadeOutMs),
                    ),
            )
    }
}
