package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAutoActions
import io.github.kentprodan.mediatheq.core.datastore.StoredOutsideControls
import io.github.kentprodan.mediatheq.core.datastore.StoredPlaybackBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioPreferencesRepository
    @Inject
    constructor(
        private val store: AudioPreferencesStore,
    ) : AudioPreferencesRepository {
        // setBehavior reads the switches, then writes them: one at a time.
        private val lock = Mutex()

        override val preferences: Flow<AudioPreferences> =
            store.values.map { stored ->
                val defaults = AudioPreferences()
                AudioPreferences(
                    unmeteredQuality =
                        enumOrNull<StreamingQuality>(stored.unmeteredQuality) ?: defaults.unmeteredQuality,
                    meteredQuality = enumOrNull<StreamingQuality>(stored.meteredQuality) ?: defaults.meteredQuality,
                    streamingCacheGb =
                        stored.streamingCacheGb?.takeIf { it in AudioPreferences.CACHE_SIZES_GB }
                            ?: defaults.streamingCacheGb,
                    downloadsUnmeteredOnly = stored.downloadsUnmeteredOnly ?: defaults.downloadsUnmeteredOnly,
                    downloadQuality =
                        enumOrNull<StreamingQuality>(stored.downloadQuality)
                            ?.takeIf { it in AudioPreferences.DOWNLOAD_QUALITIES }
                            ?: defaults.downloadQuality,
                    autoDownloads = enumOrNull<AutoDownloads>(stored.autoDownloads) ?: defaults.autoDownloads,
                    removeUnfavorited = stored.removeUnfavorited ?: defaults.removeUnfavorited,
                    maxParallelDownloads =
                        stored.maxParallelDownloads?.takeIf { it in AudioPreferences.PARALLEL_DOWNLOADS }
                            ?: defaults.maxParallelDownloads,
                    forceInstantTranscoding = stored.forceInstantTranscoding ?: defaults.forceInstantTranscoding,
                    behavior = behavior(stored.behavior),
                    audioFocus = audioFocus(stored.outside),
                    headset = headset(stored.outside),
                    notificationButtons = notificationButtons(stored.outside),
                    autoActions = autoActions(stored.autoActions),
                    transitions =
                        TransitionsPreferences(
                            fadeInMs = TransitionsPreferences.fadeMs(stored.fadeInMs ?: 0),
                            fadeOutMs = TransitionsPreferences.fadeMs(stored.fadeOutMs ?: 0),
                        ),
                )
            }

        override suspend fun setStreamingQuality(
            metered: Boolean,
            quality: StreamingQuality,
        ) = store.setStreamingQuality(metered, quality.name)

        override suspend fun setStreamingCacheGb(gb: Int) {
            if (gb in AudioPreferences.CACHE_SIZES_GB) store.setStreamingCacheGb(gb)
        }

        override suspend fun setDownloadsUnmeteredOnly(on: Boolean) = store.setDownloadsUnmeteredOnly(on)

        override suspend fun setDownloadQuality(quality: StreamingQuality) {
            if (quality in AudioPreferences.DOWNLOAD_QUALITIES) store.setDownloadQuality(quality.name)
        }

        override suspend fun setMaxParallelDownloads(count: Int) {
            if (count in AudioPreferences.PARALLEL_DOWNLOADS) store.setMaxParallelDownloads(count)
        }

        override suspend fun setAutoDownloads(mode: AutoDownloads) = store.setAutoDownloads(mode.name)

        override suspend fun setRemoveUnfavorited(on: Boolean) = store.setRemoveUnfavorited(on)

        override suspend fun restoreDownloadDefaults() = store.clearDownloads()

        override suspend fun setForceInstantTranscoding(on: Boolean) = store.setForceInstantTranscoding(on)

        override suspend fun restoreDecodingDefaults() = store.clearDecoding()

        override suspend fun setBehavior(
            switch: BehaviorSwitch,
            on: Boolean,
        ) {
            // Read, change one switch, write: one at a time, so quick taps never undo each other.
            lock.withLock { writeBehavior(preferences.first().behavior.with(switch, on)) }
        }

        private suspend fun writeBehavior(behavior: PlaybackBehavior) =
            store.setBehavior(
                StoredPlaybackBehavior(
                    preservePlayNextOrder = behavior.preservePlayNextOrder,
                    rewindFirst = behavior.rewindFirst,
                    circular = behavior.circular,
                    keepPausedOnSkip = behavior.keepPausedOnSkip,
                    stopRepeatOneOnSkip = behavior.stopRepeatOneOnSkip,
                    retainPlayerState = behavior.retainPlayerState,
                    weightedShuffle = behavior.weightedShuffle,
                    bluetoothLyrics = behavior.bluetoothLyrics,
                ),
            )

        override suspend fun restoreBehaviorDefaults() = store.setBehavior(StoredPlaybackBehavior())

        override suspend fun setAudioFocus(audioFocus: AudioFocusPreferences?) =
            store.setAudioFocus(audioFocus?.shortAction?.name, audioFocus?.noResumeAfterLongLoss)

        override suspend fun setHeadset(headset: HeadsetPreferences?) =
            store.setHeadset(
                headset?.let {
                    listOf(it.singleClick.name, it.doubleClick.name, it.tripleClick.name) to
                        (it.nextButton.name to it.previousButton.name)
                },
                headset?.slowClicks,
            )

        override suspend fun setNotificationButtons(buttons: NotificationButtonsPreferences?) =
            store.setNotificationButtons(
                buttons?.let { listOf(it.action1.name, it.action2.name, it.action3.name) },
                buttons?.showPrevious,
                buttons?.showNext,
            )

        override suspend fun setAutoActions(autoActions: AutoActionsPreferences?) =
            store.setAutoActions(
                StoredAutoActions(
                    volumePause = autoActions?.volumePause,
                    headphonePlay = autoActions?.headphonePlay,
                    bluetoothPlay = autoActions?.bluetoothPlay,
                    pauseOnTaskRemoval = autoActions?.pauseOnTaskRemoval,
                    ignoreRemoteStop = autoActions?.ignoreRemoteStop,
                ),
            )

        override suspend fun setTransitions(transitions: TransitionsPreferences?) =
            store.setFades(
                transitions?.fadeInMs?.let(TransitionsPreferences::fadeMs),
                transitions?.fadeOutMs?.let(TransitionsPreferences::fadeMs),
            )

        private fun autoActions(stored: StoredAutoActions): AutoActionsPreferences {
            val defaults = AutoActionsPreferences()
            return AutoActionsPreferences(
                volumePause = stored.volumePause ?: defaults.volumePause,
                headphonePlay = stored.headphonePlay ?: defaults.headphonePlay,
                bluetoothPlay = stored.bluetoothPlay ?: defaults.bluetoothPlay,
                pauseOnTaskRemoval = stored.pauseOnTaskRemoval ?: defaults.pauseOnTaskRemoval,
                ignoreRemoteStop = stored.ignoreRemoteStop ?: defaults.ignoreRemoteStop,
            )
        }

        private fun audioFocus(stored: StoredOutsideControls): AudioFocusPreferences {
            val defaults = AudioFocusPreferences()
            return AudioFocusPreferences(
                shortAction = enumOrNull<ShortFocusAction>(stored.focusShortAction) ?: defaults.shortAction,
                noResumeAfterLongLoss = stored.focusNoResume ?: defaults.noResumeAfterLongLoss,
            )
        }

        private fun headset(stored: StoredOutsideControls): HeadsetPreferences {
            val defaults = HeadsetPreferences()
            return HeadsetPreferences(
                singleClick = enumOrNull<HeadsetAction>(stored.headsetSingle) ?: defaults.singleClick,
                doubleClick = enumOrNull<HeadsetAction>(stored.headsetDouble) ?: defaults.doubleClick,
                tripleClick = enumOrNull<HeadsetAction>(stored.headsetTriple) ?: defaults.tripleClick,
                nextButton = enumOrNull<NextButtonAction>(stored.headsetNext) ?: defaults.nextButton,
                previousButton = enumOrNull<PreviousButtonAction>(stored.headsetPrevious) ?: defaults.previousButton,
                slowClicks = stored.headsetSlow ?: defaults.slowClicks,
            )
        }

        private fun notificationButtons(stored: StoredOutsideControls): NotificationButtonsPreferences {
            val defaults = NotificationButtonsPreferences()
            return NotificationButtonsPreferences(
                showPrevious = stored.notificationPrevious ?: defaults.showPrevious,
                showNext = stored.notificationNext ?: defaults.showNext,
                action1 = enumOrNull<SessionButtonAction>(stored.notificationAction1) ?: defaults.action1,
                action2 = enumOrNull<SessionButtonAction>(stored.notificationAction2) ?: defaults.action2,
                action3 = enumOrNull<SessionButtonAction>(stored.notificationAction3) ?: defaults.action3,
            )
        }

        private fun behavior(stored: StoredPlaybackBehavior): PlaybackBehavior {
            val defaults = PlaybackBehavior()
            return PlaybackBehavior(
                preservePlayNextOrder = stored.preservePlayNextOrder ?: defaults.preservePlayNextOrder,
                rewindFirst = stored.rewindFirst ?: defaults.rewindFirst,
                circular = stored.circular ?: defaults.circular,
                keepPausedOnSkip = stored.keepPausedOnSkip ?: defaults.keepPausedOnSkip,
                stopRepeatOneOnSkip = stored.stopRepeatOneOnSkip ?: defaults.stopRepeatOneOnSkip,
                retainPlayerState = stored.retainPlayerState ?: defaults.retainPlayerState,
                weightedShuffle = stored.weightedShuffle ?: defaults.weightedShuffle,
                bluetoothLyrics = stored.bluetoothLyrics ?: defaults.bluetoothLyrics,
            )
        }
    }
