package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAudioPreferencesRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultAudioPreferencesRepository(AudioPreferencesStore(dataStore))

    @Test
    fun `streaming defaults are original on Wi-Fi, 256 on mobile, 2 GB, Wi-Fi downloads`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences.qualityFor(metered = false)).isEqualTo(StreamingQuality.Original)
            assertThat(preferences.qualityFor(metered = true)).isEqualTo(StreamingQuality.Kbps256)
            assertThat(preferences.streamingCacheGb).isEqualTo(2)
            assertThat(preferences.downloadsUnmeteredOnly).isTrue()
        }

    @Test
    fun `streaming and storage choices are kept, each network on its own`() =
        runTest {
            repository.setStreamingQuality(metered = true, StreamingQuality.Kbps128)
            repository.setStreamingQuality(metered = false, StreamingQuality.Kbps256)
            repository.setStreamingCacheGb(8)
            repository.setDownloadsUnmeteredOnly(false)

            val preferences = repository.preferences.first()
            assertThat(preferences.meteredQuality).isEqualTo(StreamingQuality.Kbps128)
            assertThat(preferences.unmeteredQuality).isEqualTo(StreamingQuality.Kbps256)
            assertThat(preferences.streamingCacheGb).isEqualTo(8)
            assertThat(preferences.downloadsUnmeteredOnly).isFalse()
        }

    @Test
    fun `a cache size that is not offered is refused`() =
        runTest {
            repository.setStreamingCacheGb(3)

            assertThat(repository.preferences.first().streamingCacheGb).isEqualTo(2)
        }

    @Test
    fun `unknown stored names and sizes fall back to the defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("metered_quality")] = "Kbps999"
                it[
                    androidx.datastore.preferences.core
                        .intPreferencesKey("streaming_cache_gb"),
                ] = 100
            }

            assertThat(repository.preferences.first()).isEqualTo(AudioPreferences())
        }

    @Test
    fun `decoding settings are kept, and restoring them resets only that page`() =
        runTest {
            repository.setStreamingQuality(metered = true, StreamingQuality.Kbps192)
            repository.setStreamingQuality(metered = false, StreamingQuality.Kbps320)
            repository.setForceInstantTranscoding(true)
            val set = repository.preferences.first()
            assertThat(set.meteredQuality).isEqualTo(StreamingQuality.Kbps192)
            assertThat(set.unmeteredQuality).isEqualTo(StreamingQuality.Kbps320)
            assertThat(set.forceInstantTranscoding).isTrue()

            repository.restoreDecodingDefaults()

            val restored = repository.preferences.first()
            assertThat(restored.meteredQuality).isEqualTo(StreamingQuality.Kbps256)
            assertThat(restored.unmeteredQuality).isEqualTo(StreamingQuality.Original)
            assertThat(restored.forceInstantTranscoding).isFalse()
        }

    @Test
    fun `playback behavior is kept, and restoring it resets only that page`() =
        runTest {
            val behavior =
                PlaybackBehavior(
                    preservePlayNextOrder = true,
                    rewindFirst = false,
                    circular = false,
                    keepPausedOnSkip = true,
                    stopRepeatOneOnSkip = false,
                    retainPlayerState = true,
                    weightedShuffle = false,
                )
            BehaviorSwitch.entries.forEach { repository.setBehavior(it, behavior.isOn(it)) }
            repository.setForceInstantTranscoding(true)
            assertThat(repository.preferences.first().behavior).isEqualTo(behavior)

            repository.restoreBehaviorDefaults()

            assertThat(repository.preferences.first().behavior).isEqualTo(PlaybackBehavior())
            assertThat(repository.preferences.first().forceInstantTranscoding).isTrue()
        }

    @Test
    fun `quick behavior switches each keep the other one`() =
        runTest {
            coroutineScope {
                launch { repository.setBehavior(BehaviorSwitch.Circular, false) }
                launch { repository.setBehavior(BehaviorSwitch.WeightedShuffle, false) }
            }

            val behavior = repository.preferences.first().behavior
            assertThat(behavior.circular).isFalse()
            assertThat(behavior.weightedShuffle).isFalse()
        }

    @Test
    fun `audio focus, headset and notification buttons are kept, and each restore resets only its page`() =
        runTest {
            val focus = AudioFocusPreferences(ShortFocusAction.Pause, noResumeAfterLongLoss = true)
            val headset =
                HeadsetPreferences(
                    singleClick = HeadsetAction.Nothing,
                    doubleClick = HeadsetAction.Previous,
                    tripleClick = HeadsetAction.Next,
                    nextButton = NextButtonAction.SeekForward,
                    previousButton = PreviousButtonAction.SeekBack,
                    slowClicks = true,
                )
            val buttons =
                NotificationButtonsPreferences(
                    showPrevious = false,
                    showNext = false,
                    action1 = SessionButtonAction.Shuffle,
                    action2 = SessionButtonAction.Nothing,
                    action3 = SessionButtonAction.Favorite,
                )
            repository.setAudioFocus(focus)
            repository.setHeadset(headset)
            repository.setNotificationButtons(buttons)
            val set = repository.preferences.first()
            assertThat(set.audioFocus).isEqualTo(focus)
            assertThat(set.headset).isEqualTo(headset)
            assertThat(set.notificationButtons).isEqualTo(buttons)
            assertThat(set.notificationButtons.actions)
                .containsExactly(SessionButtonAction.Shuffle, SessionButtonAction.Favorite)
                .inOrder()

            repository.setHeadset(null)

            val restored = repository.preferences.first()
            assertThat(restored.headset).isEqualTo(HeadsetPreferences())
            assertThat(restored.audioFocus).isEqualTo(focus)
            assertThat(restored.notificationButtons).isEqualTo(buttons)
            repository.setAudioFocus(null)
            repository.setNotificationButtons(null)
            assertThat(repository.preferences.first().audioFocus).isEqualTo(AudioFocusPreferences())
            assertThat(repository.preferences.first().notificationButtons).isEqualTo(NotificationButtonsPreferences())
        }

    @Test
    fun `automatic actions default to headphone auto play and ignore remote stop`() =
        runTest {
            assertThat(repository.preferences.first().autoActions)
                .isEqualTo(
                    AutoActionsPreferences(
                        volumePause = false,
                        headphonePlay = true,
                        bluetoothPlay = false,
                        pauseOnTaskRemoval = false,
                        ignoreRemoteStop = true,
                    ),
                )
        }

    @Test
    fun `fades are off by default, kept in steps of 100 ms up to a second, and restore resets only that page`() =
        runTest {
            assertThat(repository.preferences.first().transitions).isEqualTo(TransitionsPreferences(0, 0))
            val actions = AutoActionsPreferences(volumePause = true)
            repository.setAutoActions(actions)

            repository.setTransitions(TransitionsPreferences(fadeInMs = 370, fadeOutMs = 5_000))
            assertThat(repository.preferences.first().transitions).isEqualTo(TransitionsPreferences(300, 1_000))

            repository.setTransitions(null)
            val restored = repository.preferences.first()
            assertThat(restored.transitions).isEqualTo(TransitionsPreferences())
            assertThat(restored.autoActions).isEqualTo(actions)
        }

    @Test
    fun `automatic actions are kept, and their restore resets only that page`() =
        runTest {
            val focus = AudioFocusPreferences(ShortFocusAction.Pause, noResumeAfterLongLoss = true)
            val actions =
                AutoActionsPreferences(
                    volumePause = true,
                    headphonePlay = false,
                    bluetoothPlay = true,
                    pauseOnTaskRemoval = true,
                    ignoreRemoteStop = false,
                )
            repository.setAudioFocus(focus)
            repository.setAutoActions(actions)
            assertThat(repository.preferences.first().autoActions).isEqualTo(actions)

            repository.setAutoActions(null)

            val restored = repository.preferences.first()
            assertThat(restored.autoActions).isEqualTo(AutoActionsPreferences())
            assertThat(restored.audioFocus).isEqualTo(focus)
        }
}
