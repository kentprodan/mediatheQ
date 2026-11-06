package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
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
        // setEqBand reads the gains, then writes them: one at a time.
        private val eqLock = Mutex()

        override val preferences: Flow<AudioPreferences> =
            store.values.map { stored ->
                val defaults = AudioPreferences()
                AudioPreferences(
                    bitPerfectUsb = stored.bitPerfectUsb ?: defaults.bitPerfectUsb,
                    equalizerOn = stored.equalizerOn ?: defaults.equalizerOn,
                    eqPreset = EqPreset.entries.firstOrNull { it.name == stored.eqPreset } ?: defaults.eqPreset,
                    customGainsDb = stored.eqBands?.toGains() ?: defaults.customGainsDb,
                    normalization = enumOrNull<Normalization>(stored.normalization) ?: defaults.normalization,
                    unmeteredQuality =
                        enumOrNull<StreamingQuality>(stored.unmeteredQuality) ?: defaults.unmeteredQuality,
                    meteredQuality = enumOrNull<StreamingQuality>(stored.meteredQuality) ?: defaults.meteredQuality,
                    streamingCacheGb =
                        stored.streamingCacheGb?.takeIf { it in AudioPreferences.CACHE_SIZES_GB }
                            ?: defaults.streamingCacheGb,
                    downloadsUnmeteredOnly = stored.downloadsUnmeteredOnly ?: defaults.downloadsUnmeteredOnly,
                    forceInstantTranscoding = stored.forceInstantTranscoding ?: defaults.forceInstantTranscoding,
                    behavior = behavior(stored.behavior),
                    audioFocus = audioFocus(stored.outside),
                    headset = headset(stored.outside),
                    notificationButtons = notificationButtons(stored.outside),
                )
            }

        override suspend fun setBitPerfectUsb(on: Boolean) = store.setBitPerfectUsb(on)

        override suspend fun setEqualizerOn(on: Boolean) = store.setEqualizerOn(on)

        override suspend fun setEqPreset(preset: EqPreset) = store.setEq(preset.name, bands = null)

        override suspend fun setNormalization(normalization: Normalization) = store.setNormalization(normalization.name)

        override suspend fun setStreamingQuality(
            metered: Boolean,
            quality: StreamingQuality,
        ) = store.setStreamingQuality(metered, quality.name)

        override suspend fun setStreamingCacheGb(gb: Int) {
            if (gb in AudioPreferences.CACHE_SIZES_GB) store.setStreamingCacheGb(gb)
        }

        override suspend fun setDownloadsUnmeteredOnly(on: Boolean) = store.setDownloadsUnmeteredOnly(on)

        override suspend fun setForceInstantTranscoding(on: Boolean) = store.setForceInstantTranscoding(on)

        override suspend fun restoreDecodingDefaults() = store.clearDecoding()

        override suspend fun setBehavior(
            switch: BehaviorSwitch,
            on: Boolean,
        ) {
            // Read, change one switch, write: one at a time, so quick taps never undo each other.
            eqLock.withLock { writeBehavior(preferences.first().behavior.with(switch, on)) }
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
            )
        }

        override suspend fun setEqBand(
            index: Int,
            gainDb: Float,
        ) {
            eqLock.withLock {
                val gains = withEqBand(preferences.first().eqGainsDb, index, gainDb) ?: return
                store.setEq(EqPreset.Custom.name, gains.joinToString(","))
            }
        }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }

        // Anything but one valid gain per band is dropped: an old or damaged value means the defaults.
        private fun String.toGains(): List<Float>? =
            split(",")
                .map { it.toFloatOrNull()?.takeIf { gain -> gain in AudioPreferences.EQ_GAIN_DB } ?: return null }
                .takeIf { it.size == AudioPreferences.EQ_BANDS_HZ.size }
    }
