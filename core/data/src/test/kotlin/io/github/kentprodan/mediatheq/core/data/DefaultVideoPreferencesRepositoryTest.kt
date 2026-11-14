package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredVideoPreferences
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.SkipMode
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultVideoPreferencesRepositoryTest {
    private val store = AudioPreferencesStore(InMemoryPreferencesDataStore())
    private val repository = DefaultVideoPreferencesRepository(store)

    @Test
    fun `the defaults are Original on Wi-Fi, 480p on mobile data, forced subtitles, auto-play and PiP on`() =
        runTest {
            val video = repository.preferences.first()

            assertThat(video.quality(metered = false)).isEqualTo(VideoQuality.Original)
            assertThat(video.quality(metered = true)).isEqualTo(VideoQuality.P480)
            assertThat(video.subtitleMode).isEqualTo(SubtitleMode.ForcedOnly)
            assertThat(video.burnInStyled).isFalse()
            assertThat(video.autoPlayNext).isTrue()
            assertThat(video.pictureInPicture).isTrue()
            assertThat(video.keepAudio).isFalse()
            assertThat(video.trackDefaults("ro")).isEqualTo(TrackDefaults("ro", SubtitleMode.ForcedOnly, "ro"))
        }

    @Test
    fun `every setting survives a round trip, and only what differs from the defaults is stored`() =
        runTest {
            val changed =
                VideoPreferences(
                    unmeteredQuality = VideoQuality.P1080,
                    meteredQuality = VideoQuality.P360,
                    audioLanguage = "en",
                    subtitleMode = SubtitleMode.Language,
                    subtitleLanguage = "ro",
                    burnInStyled = true,
                    autoPlayNext = false,
                    pictureInPicture = false,
                    keepAudio = true,
                    skipIntro = SkipMode.Auto,
                    skipCredits = SkipMode.Off,
                    skipRecapPreview = SkipMode.Auto,
                )

            repository.update { changed }
            assertThat(repository.preferences.first().skipFor(SegmentType.Commercial)).isEqualTo(SkipMode.Auto)

            assertThat(repository.preferences.first()).isEqualTo(changed)
            assertThat(repository.preferences.first().trackDefaults("de"))
                .isEqualTo(TrackDefaults("en", SubtitleMode.Language, "ro"))
            repository.update { VideoPreferences() }
            assertThat(store.values.first().video).isEqualTo(StoredVideoPreferences())
        }

    @Test
    fun `the subtitle language falls back to the audio language, which falls back to the phone's`() {
        assertThat(VideoPreferences(audioLanguage = "en").trackDefaults("ro"))
            .isEqualTo(TrackDefaults("en", SubtitleMode.ForcedOnly, "en"))
        assertThat(VideoPreferences(subtitleLanguage = "ro", subtitleMode = SubtitleMode.Language).trackDefaults("de"))
            .isEqualTo(TrackDefaults("de", SubtitleMode.Language, "ro"))
    }

    @Test
    fun `unknown stored values fall back to the defaults`() =
        runTest {
            store.setVideo(StoredVideoPreferences(unmeteredQuality = "P8K", subtitleMode = "Sometimes"))

            assertThat(repository.preferences.first()).isEqualTo(VideoPreferences())
        }
}
